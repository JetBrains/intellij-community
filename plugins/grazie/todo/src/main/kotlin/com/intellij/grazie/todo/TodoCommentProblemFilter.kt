package com.intellij.grazie.todo

import com.intellij.grazie.text.ProblemFilter
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContent.TextDomain.COMMENTS
import com.intellij.grazie.text.TextContent.TextDomain.DOCUMENTATION
import com.intellij.grazie.text.TextProblem
import com.intellij.grazie.utils.Text
import com.intellij.ide.todo.TodoConfiguration
import com.intellij.openapi.project.DumbService
import com.intellij.psi.search.PsiTodoSearchHelper
import com.intellij.psi.util.CachedValuesManager

internal class TodoCommentProblemFilter: ProblemFilter() {
  override fun shouldIgnore(problem: TextProblem): Boolean {
    val text = problem.text
    return (text.domain == COMMENTS || text.domain == DOCUMENTATION) && isTodoComment(text)
  }

  // the _todo_ word spoils the grammar of what follows
  private fun isTodoComment(text: TextContent): Boolean {
    val file = text.containingFile
    if (DumbService.isDumb(file.project)) {
      return TodoConfiguration.getInstance().todoPatterns
        .mapNotNull { it.pattern }
        .any { Text.allOccurrences(it, text).isNotEmpty() }
    }
    val todos = CachedValuesManager.getProjectPsiDependentCache(file) {
      PsiTodoSearchHelper.getInstance(it.project).findTodoItems(it)
    }
    return todos.any { text.intersectsRange(it.textRange) }
  }
}
