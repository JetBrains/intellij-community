package com.intellij.grazie.text

import com.intellij.grazie.text.TextContent.TextDomain.COMMENTS
import com.intellij.grazie.text.TextContent.TextDomain.DOCUMENTATION
import com.intellij.grazie.utils.Text
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.search.PsiTodoSearchHelper

internal class CommentProblemFilter : ProblemFilter() {

  override fun shouldIgnore(problem: TextProblem): Boolean {
    val domain = problem.text.domain
    if (domain == COMMENTS || domain == DOCUMENTATION) {
      val parent = problem.text.commonParent
      if (isTodoComment(parent.containingFile, parent.textRange)) {
        return true
      }
    }

    return domain == COMMENTS && Text.isSingleSentence(problem.text) && problem.fitsGroup(RuleGroup.UNDECORATED_SINGLE_SENTENCE)
  }

  // the _todo_ word spoils the grammar of what follows
  private fun isTodoComment(file: PsiFile, range: TextRange) =
    PsiTodoSearchHelper.SERVICE.getInstance(file.project).findTodoItems(file, range.startOffset, range.endOffset).isNotEmpty()

}