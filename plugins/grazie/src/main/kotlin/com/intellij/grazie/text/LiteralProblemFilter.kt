package com.intellij.grazie.text

import com.intellij.grazie.utils.Text

/** Enable this filter for your language to filter [RuleGroup.LITERALS] in its string literals */
class LiteralProblemFilter : ProblemFilter() {
  override fun shouldIgnore(problem: TextProblem): Boolean {
    val text = problem.text
    return text.domain == TextContent.TextDomain.LITERALS &&
           (problem.fitsGroup(RuleGroup.LITERALS) || problem.fitsGroup(RuleGroup(RuleGroup.INCOMPLETE_SENTENCE)))
  }
}
