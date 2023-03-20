package com.intellij.cce.visitor

import com.intellij.cce.actions.CompletionGolfMode
import com.intellij.cce.core.Language
import com.intellij.openapi.project.Project

class CompletionGolfFragmentBuilder(project: Project, language: Language, private val completionGolfMode: CompletionGolfMode)
  : CodeFragmentFromPsiBuilder(project, language) {
  override fun getVisitors(): List<CompletionGolfEvaluationVisitor> {
    return CompletionGolfVisitorFactory.EP_NAME.extensions.toList()
      .filter { it.language == language }.takeIf { it.isNotEmpty() }
      ?.map { it.createVisitor(completionGolfMode) }
           ?: listOf(CompletionGolfAllEvaluationVisitor.Default(language))
  }
}
