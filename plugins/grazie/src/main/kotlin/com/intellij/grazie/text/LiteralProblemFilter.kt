package com.intellij.grazie.text

/** Enable this filter for your language to filter [RuleGroup.LITERALS] in its string literals */
class LiteralProblemFilter : ProblemFilter() {
  override fun shouldIgnore(problem: TextProblem): Boolean {
    return problem.text.domain == TextContent.TextDomain.LITERALS && problem.fitsGroup(RuleGroup.LITERALS)
  }
}