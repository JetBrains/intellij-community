package com.intellij.grazie.text

import com.intellij.grazie.text.TextContent.TextDomain.COMMENTS
import com.intellij.grazie.text.TextContent.TextDomain.DOCUMENTATION
import com.intellij.grazie.utils.Text
import com.intellij.psi.PsiFile
import com.intellij.psi.search.PsiTodoSearchHelper

internal class CommentProblemFilter : ProblemFilter() {
  private val suppressedInComments =
    setOf("LanguageTool.EN.LC_AFTER_PERIOD", "LanguageTool.NL.PUNT_GEEN_HL", "LanguageTool.DE.KLEIN_NACH_PUNKT")

  override fun shouldIgnore(problem: TextProblem): Boolean {
    val text = problem.text
    val domain = text.domain
    if (domain == COMMENTS || domain == DOCUMENTATION) {
      if (isTodoComment(text.commonParent.containingFile, text)) {
        return true
      }
      if (problem.rule.globalId.endsWith("DOUBLE_PUNCTUATION") && isNumberRange(problem, text)) {
        return true
      }
    }

    if (domain == DOCUMENTATION) {
      return problem.highlightRange.startOffset == 0 && problem.fitsGroup(RuleGroup(RuleGroup.INCOMPLETE_SENTENCE))
    }

    if (domain == COMMENTS) {
      if (problem.rule.globalId in suppressedInComments) {
        return true
      }
      if (Text.isSingleSentence(text) && problem.fitsGroup(RuleGroup.UNDECORATED_SINGLE_SENTENCE)) {
        return true
      }
    }
    return false
  }

  private fun isNumberRange(problem: TextProblem, text: TextContent): Boolean {
    val range = problem.highlightRange
    return range.startOffset > 0 && range.endOffset < text.length &&
           text[range.startOffset - 1].isDigit() && text[range.endOffset].isDigit()
  }

  // the _todo_ word spoils the grammar of what follows
  private fun isTodoComment(file: PsiFile, text: TextContent) =
    PsiTodoSearchHelper.SERVICE.getInstance(file.project).findTodoItems(file).any { text.intersectsRange(it.textRange) }

}