package com.intellij.grazie.ide.language.markdown

import com.intellij.grazie.text.ProblemFilter
import com.intellij.grazie.text.RuleGroup
import com.intellij.grazie.text.TextProblem

internal class MarkdownProblemFilter : ProblemFilter() {
  private val ignoredInHeader = RuleGroup("LanguageTool.EN.SENT_START_NUM", RuleGroup.SENTENCE_END_PUNCTUATION)

  override fun shouldIgnore(problem: TextProblem): Boolean {
    val parent = problem.text.commonParent
    return MarkdownPsiUtils.isInOuterListItem(parent) && problem.fitsGroup(RuleGroup.UNDECORATED_SINGLE_SENTENCE) ||
           MarkdownPsiUtils.isHeaderContent(parent) && problem.fitsGroup(ignoredInHeader)
  }
}