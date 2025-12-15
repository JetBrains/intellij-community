package com.intellij.grazie.text

import com.intellij.grazie.utils.HighlightingUtil
import com.intellij.grazie.utils.Text.findParagraphRange
import com.intellij.grazie.utils.Text.isSingleSentence
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.ui.CommitMessage

internal class CommitProblemFilter : ProblemFilter() {
  override fun shouldIgnore(problem: TextProblem): Boolean {
    if (!isCommitMessage(problem)) {
      return false
    }

    val text = problem.text
    val stripOffset = HighlightingUtil.stripPrefix(text)
    val range = problem.highlightRanges.first()
    if (range.startOffset < stripOffset) {
      return isCasingIssue(text, range, problem)
    }

    val strippedText = text.subSequence(stripOffset, text.length)
    val shiftedRange = range.shiftLeft(stripOffset)
    if (!isSingleLowercaseSentence(strippedText, shiftedRange)) {
      return false
    }
    return isCasingIssue(strippedText, shiftedRange, problem)
  }

  private fun isCasingIssue(text: CharSequence, range: TextRange, problem: TextProblem): Boolean {
    val phrase = range.subSequence(text).toString()
    return problem.suggestions.asSequence()
      .map { it.presentableText }
      .any { it.equals(phrase, ignoreCase = true) }
  }

  override fun shouldIgnoreTypo(problem: TextProblem): Boolean = shouldIgnore(problem)

  private fun isCommitMessage(problem: TextProblem): Boolean =
    problem.text.domain == TextContent.TextDomain.PLAIN_TEXT && CommitMessage.isCommitMessage(problem.text.containingFile)

  private fun isSingleLowercaseSentence(text: CharSequence, range: TextRange): Boolean {
    val paragraphText = findParagraphRange(text, range).subSequence(text)
    return isSingleSentence(paragraphText) && HighlightingUtil.isLowercase(paragraphText)
  }
}