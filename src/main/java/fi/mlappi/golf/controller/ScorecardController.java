package fi.mlappi.golf.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import fi.mlappi.golf.model.Game;
import fi.mlappi.golf.model.Player;
import fi.mlappi.golf.model.Round;
import fi.mlappi.golf.model.Scorecard;
import fi.mlappi.golf.service.GameService;
import fi.mlappi.golf.service.PlayerService;
import fi.mlappi.golf.service.ScorecardService;
import lombok.extern.slf4j.Slf4j;

@Controller
@Slf4j
public class ScorecardController {

	@Autowired
	GameService gameService;
	@Autowired
	ScorecardService scoreService;
	@Autowired
	PlayerService playerService;

	@RequestMapping("/score/leaderboard/{id}")
	public String leaderboard(ModelMap model, @PathVariable("id") long id) {
		List<LeaderboardScore> scoreList = new ArrayList<>();
		List<Long> rounds = new ArrayList<>();
		Map<Long, LeaderboardScore> scoreMap = new HashMap<>();
		Game game = gameService.find(id);
		for (Round r : game.getRound()) {
			rounds.add(r.getId());
			List<Scorecard> scorecards = scoreService.findByRoundId(r.getId());
			for (Scorecard s : scorecards) {

				if (!scoreMap.containsKey(s.getPlayer().getId())) {
					LeaderboardScore lbs = new LeaderboardScore();
					lbs.setName(s.getPlayer().getFirstName() + " " + s.getPlayer().getLastName());
					scoreMap.put(s.getPlayer().getId(), lbs);
				}

				LeaderboardScore lbs = scoreMap.get(s.getPlayer().getId());
				int total = s.getCountTotal();
				lbs.getScore().add(total);
				if (total > 0) {
					lbs.setThru(lbs.getThru() + 18);
					lbs.setTotalAll(lbs.getTotalAll() + total);
					lbs.setTotal(lbs.getTotal() + (total - s.getRound().getCourse().getCountTotal()));
				}				
			}
		}

		scoreList.addAll(scoreMap.values());
		
		fillScores(scoreList, rounds);
		
		scoreList.sort((LeaderboardScore s1, LeaderboardScore s2) -> Double.valueOf(s1.getTotalAll())
				.compareTo(Double.valueOf(s2.getTotalAll())));
		model.addAttribute("scores", scoreList);
		model.addAttribute("gameId", id);
		model.addAttribute("rounds", rounds);

		return "leaderboard";
	}

	private void fillScores(List<LeaderboardScore> scoreList, List<Long> rounds) {
		for(LeaderboardScore s : scoreList) {
			if(s.getScore().size() < rounds.size()) {
				s.getScore().add(0);
				fillScores(scoreList, rounds);
			}
		}
	}

	@RequestMapping("/score/add/{id}")
	public String add(ModelMap model, @PathVariable("id") long roundId) {
		log.debug("add a new score. round:" + roundId);
		Scorecard scorecard = new Scorecard();
		scorecard.setRound(gameService.findRound(roundId));
		addModelValues(model, scorecard);

		return "new-scorecard";
	}

	private void addModelValues(ModelMap model, Scorecard s) {
		model.addAttribute("score", s);
		if (s.getPlayer() != null)
			model.addAttribute("playerId", s.getPlayer().getId());
		model.addAttribute("gameId", s.getRound().getGame().getId());
		model.addAttribute("roundId", s.getRound().getId());

		Map<Long, String> playerList = new LinkedHashMap<Long, String>();
		for (Player player : playerService.getAllPlayers()) {
			playerList.put(player.getId(), player.getFirstName() + " " + player.getLastName());
		}
		model.addAttribute("playerList", playerList);

	}

	@RequestMapping(value = "/score/edit/{id}")
	public String edit(ModelMap model, @PathVariable("id") long id) {
		log.debug("edit score " + id);
		Scorecard s = scoreService.find(id);
		addModelValues(model, s);

		return "new-scorecard";
	}

	@RequestMapping(value = "/score/list/{gameId}")
	public String scoreList(ModelMap model, @PathVariable("gameId") long gameId,
			@ModelAttribute("round") RoundSelect roundSelect) {
		log.debug("scoreList game id  " + gameId);
		log.debug("round " + roundSelect);
		Game game = gameService.find(gameId);
		Map<Long, String> roundList = new LinkedHashMap<Long, String>();
		int i = 0;
		for (Round r : game.getRound()) {
			if (i == 0 && roundSelect.getRoundId() == 0) {
				roundSelect.setRoundId(r.getId());
			}
			roundList.put(r.getId(), r.getName() + " | " + r.getCourse().getName());
			i++;
		}

		List<Scorecard> scores = scoreService.findByRoundId(roundSelect.getRoundId());
		scores = scoreService.countWins(roundSelect.getRoundId());
		scores.sort((Scorecard s1, Scorecard s2) -> Double.valueOf(s2.getWin()).compareTo(Double.valueOf(s1.getWin())));
		model.addAttribute("game", game);
		model.addAttribute("scoreList", scores);
		model.addAttribute("roundList", roundList);
		model.addAttribute("round", roundSelect);

		return "list-scores";
	}

	@RequestMapping(value = "/score/remove/{id}")
	public String remove(ModelMap model, @PathVariable("id") long id) {
		log.debug("remove game " + id);
		Scorecard s = scoreService.find(id);
		Round round = s.getRound();
		scoreService.delete(id);
		model.put("message", "The scorecard removed.");
		RoundSelect rs = new RoundSelect();
		rs.setRoundId(round.getId());
		return "redirect:/score/list/" + round.getGame().getId();
	}

	@RequestMapping(value = "/score/save", method = RequestMethod.POST)
	public String save(ModelMap model, @ModelAttribute("score") @Valid Scorecard score,
			@RequestParam("gameId") Long gameId, // @RequestParam("playerId")
													// Long playerId,
			@RequestParam("roundId") Long roundId, BindingResult result) {
		log.debug("save: " + score.toString());
		log.debug("game: " + gameId);
		log.debug("round: " + roundId);

		score.setRound(gameService.findRound(roundId));
		score.setPlayer(playerService.find(score.getPlayer().getId()));
		boolean newScorecard = score.getId() == null ? true : false;
		if (!result.hasErrors()) {
			scoreService.save(score);
			if (newScorecard)
				model.put("message", "The new scorecard has been successfully created.");
			else
				model.put("message", "The scorecard has been successfully updated.");
			model.put("idScore", score.getId());
		} else {
			log.warn("jotain väärin... " + result.getFieldError());
			return "new-scorecard";
		}
		RoundSelect rs = new RoundSelect();
		rs.setRoundId(roundId);

		// scoreService.countWins(roundId);

		return "redirect:/score/list/" + gameId;
	}

}
