package com.intellij.grazie.text

import com.intellij.grazie.utils.Text

/** Enable this filter for your language to filter [RuleGroup.LITERALS] in its string literals */
class LiteralProblemFilter : ProblemFilter() {
  private val ignoredRules = RuleGroup.LITERALS

  override fun shouldIgnore(problem: TextProblem): Boolean {
    return problem.text.domain == TextContent.TextDomain.LITERALS &&
           Text.isSingleSentence(problem.text) &&
           problem.fitsGroup(ignoredRules)
  }
}