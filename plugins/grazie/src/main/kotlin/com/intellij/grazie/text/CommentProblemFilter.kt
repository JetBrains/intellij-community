package com.intellij.grazie.text

import ai.grazie.nlp.tokenizer.sentence.StandardSentenceTokenizer
import com.intellij.grazie.text.TextContent.TextDomain.COMMENTS
import com.intellij.grazie.text.TextContent.TextDomain.DOCUMENTATION
import com.intellij.grazie.utils.ProblemFilterUtil
import com.intellij.grazie.utils.Text
import com.intellij.ide.todo.TodoConfiguration
import com.intellij.openapi.project.DumbService
import com.intellij.psi.search.PsiTodoSearchHelper
import com.intellij.psi.util.CachedValuesManager

internal class CommentProblemFilter : ProblemFilter() {
  private val tokenizer
    get() = StandardSentenceTokenizer.Default

  override fun shouldIgnore(problem: TextProblem): Boolean {
    val text = problem.text
    val domain = text.domain
    if (domain == COMMENTS || domain == DOCUMENTATION) {
      if (isTodoComment(text)) {
        return true
      }
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
      if (Text.isSingleSentence(text) &&
          (problem.fitsGroup(RuleGroup.UNDECORATED_SINGLE_SENTENCE) || ProblemFilterUtil.isMissingArticleIssue(problem))) {
        return true
      }
    }
    return false
  }

  private fun isInFirstSentence(problem: TextProblem) =
    tokenizer.tokenize(problem.text.substring(0, problem.highlightRanges[0].startOffset)).size <= 1

  private fun isAboutIdentifierParts(problem: TextProblem, text: TextContent): Boolean {
    val ranges = problem.highlightRanges
    return ranges.any { text.subSequence(0, it.startOffset).endsWith('_') || text.subSequence(it.endOffset, text.length).startsWith('_') }
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
