package com.intellij.grazie.text

import com.intellij.grazie.utils.HighlightingUtil
import com.intellij.grazie.utils.Text.findParagraphRange
import com.intellij.grazie.utils.Text.isSingleSentence
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.ui.CommitMessage

internal class CommitProblemFilter : ProblemFilter() {
  override fun shouldIgnore(problem: TextProblem): Boolean = shouldIgnoreProblem(problem)
  override fun shouldIgnoreTypo(problem: TextProblem): Boolean = shouldIgnoreProblem(problem)

  private fun shouldIgnoreProblem(problem: TextProblem): Boolean {
    if (!isCommitMessage(problem)) {
      return false
    }
    val text = problem.text
    val stripOffset = HighlightingUtil.stripPrefix(text)
    val strippedText = text.subSequence(stripOffset, text.length)
    val shiftedRange = problem.highlightRanges.first().shiftLeft(stripOffset)
    if (!isSingleLowercaseSentence(strippedText, shiftedRange)) {
      return false
    }
    val phrase = shiftedRange.subSequence(strippedText).toString()
    return problem.suggestions.asSequence()
      .map { it.presentableText }
      .any { it.equals(phrase, ignoreCase = true) }
  }

  private fun isCommitMessage(problem: TextProblem): Boolean =
    problem.text.domain == TextContent.TextDomain.PLAIN_TEXT && CommitMessage.isCommitMessage(problem.text.containingFile)

  private fun isSingleLowercaseSentence(text: CharSequence, range: TextRange): Boolean {
    val paragraphText = findParagraphRange(text, range).subSequence(text)
    return isSingleSentence(paragraphText) && HighlightingUtil.isLowercase(paragraphText)
  }
}