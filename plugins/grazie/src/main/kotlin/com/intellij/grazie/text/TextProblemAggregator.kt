package com.intellij.grazie.text

import ai.grazie.gec.model.CorrectionServiceType
import ai.grazie.gec.model.CorrectionServiceType.MLEC
import ai.grazie.gec.model.CorrectionServiceType.OTHER
import ai.grazie.gec.model.CorrectionServiceType.RULE
import ai.grazie.gec.model.problem.ProblemAggregator
import ai.grazie.gec.model.problem.ProblemFix
import ai.grazie.gec.model.problem.concedeToOtherGrammarCheckers
import ai.grazie.text.TextRange
import com.intellij.grazie.mlec.MlecChecker
import com.intellij.grazie.utils.aiRange


object TextProblemAggregator : ProblemAggregator<TextProblem>() {
  override fun concedeToOtherGrammarCheckers(problem: TextProblem): Boolean {
    if (problem !is GrazieProblem) return false
    return concedeToOtherGrammarCheckers(problem.source)
  }

  override fun service(problem: TextProblem): CorrectionServiceType {
    if (problem !is GrazieProblem) return OTHER
    return if (problem.rule.globalId.startsWith(MlecChecker.Constants.MLEC_RULE_PREFIX)) MLEC else RULE
  }

  override fun isSpellingProblem(problem: TextProblem): Boolean = problem.isSpellingProblem

  override fun highlightingRanges(problem: TextProblem): List<TextRange> = problem.highlightRanges.map { it.aiRange() }

  override fun fixes(problem: TextProblem): List<ProblemFix> {
    if (problem !is GrazieProblem) return emptyList()
    return problem.source.fixes.toList()
  }

  override fun withFixes(problem: TextProblem, fixes: List<ProblemFix>): TextProblem? {
    if (problem !is GrazieProblem) return null
    return problem.copyWithProblemFixes(fixes)
  }

  override fun problemPriority(problem: TextProblem): Int {
    val concedeToOtherCheckers = concedeToOtherGrammarCheckers(problem)
    val service = service(problem)
    val styleLike = problem.isStyleLike

    if (service == RULE && !concedeToOtherCheckers && !styleLike) return 40
    if (service == OTHER && !styleLike) return 30
    if (service == MLEC) return 20
    if (service == RULE && !styleLike) return 10
    return 0
  }
}
