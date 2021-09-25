package com.intellij.grazie.text

import com.intellij.grazie.utils.Text

internal class CommentProblemFilter : ProblemFilter() {

  override fun shouldIgnore(problem: TextProblem): Boolean {
    return problem.text.domain == TextContent.TextDomain.COMMENTS &&
           Text.isSingleSentence(problem.text) &&
           problem.fitsGroup(RuleGroup.UNDECORATED_SINGLE_SENTENCE)
  }

}