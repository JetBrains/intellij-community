package com.intellij.grazie.text

import com.intellij.grazie.rule.SentenceTokenizer
import com.intellij.grazie.text.TextContent.TextDomain.COMMENTS
import com.intellij.grazie.text.TextContent.TextDomain.DOCUMENTATION
import com.intellij.grazie.utils.Text

internal class CommentProblemFilter : ProblemFilter() {

  override fun shouldIgnore(problem: TextProblem): Boolean {
    val text = problem.text
    val domain = text.domain
    if (domain == COMMENTS || domain == DOCUMENTATION) {
      if (problem.rule.globalId.startsWith("LanguageTool.") && isAboutIdentifierParts(problem, text)) {
        return true
      }
      if (isInFirstSentence(problem) && problem.fitsGroup(RuleGroup(RuleGroup.INCOMPLETE_SENTENCE))) {
        return true
      }
    }

    if (domain == COMMENTS) {
      if (problem.fitsGroup(RuleGroup(RuleGroup.UNDECORATED_SENTENCE_SEPARATION))) {
        return true
      }
      if (Text.isSingleSentence(text) && problem.fitsGroup(RuleGroup.UNDECORATED_SINGLE_SENTENCE)) {
        return true
      }
    }
    return false
  }

  private fun isInFirstSentence(problem: TextProblem): Boolean {
    return SentenceTokenizer.tokenize(problem.text)
             .firstOrNull()
             ?.range()
             ?.containsOffset(problem.highlightRanges[0].startOffset) ?: false
  }

  private fun isAboutIdentifierParts(problem: TextProblem, text: TextContent): Boolean {
    val ranges = problem.highlightRanges
    return ranges.any { text.subSequence(0, it.startOffset).endsWith('_') || text.subSequence(it.endOffset, text.length).startsWith('_') }
  }
}
