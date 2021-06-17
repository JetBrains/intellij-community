package com.intellij.grazie.text

import com.intellij.grazie.text.TextContent.TextDomain.COMMENTS
import com.intellij.grazie.text.TextContent.TextDomain.DOCUMENTATION
import com.intellij.grazie.utils.Text
import com.intellij.psi.PsiFile
import com.intellij.psi.search.PsiTodoSearchHelper

internal class CommentProblemFilter : ProblemFilter() {

  override fun shouldIgnore(problem: TextProblem): Boolean {
    val text = problem.text
    val domain = text.domain
    if ((domain == COMMENTS || domain == DOCUMENTATION) && isTodoComment(text.commonParent.containingFile, text)) {
      return true
    }

    if (domain == DOCUMENTATION) {
      return problem.highlightRange.startOffset == 0 && problem.fitsGroup(RuleGroup(RuleGroup.INCOMPLETE_SENTENCE))
    }

    return domain == COMMENTS && Text.isSingleSentence(text) && problem.fitsGroup(RuleGroup.UNDECORATED_SINGLE_SENTENCE)
  }

  // the _todo_ word spoils the grammar of what follows
  private fun isTodoComment(file: PsiFile, text: TextContent) =
    PsiTodoSearchHelper.SERVICE.getInstance(file.project).findTodoItems(file).any { text.intersectsRange(it.textRange) }

}