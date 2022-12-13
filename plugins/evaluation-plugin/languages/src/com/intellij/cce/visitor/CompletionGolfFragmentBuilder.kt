package com.intellij.cce.visitor

import com.intellij.cce.core.Language
import com.intellij.openapi.project.Project

class CompletionGolfFragmentBuilder(project: Project, language: Language) : CodeFragmentFromPsiBuilder(project, language) {
  override fun getVisitors(): List<CompletionGolfEvaluationVisitor> {
    return CompletionGolfEvaluationVisitor.EP_NAME.extensions.toList()
             .filter { it.language == language }.takeIf { it.isNotEmpty() }
           ?: listOf(CompletionGolfEvaluationVisitor.Default(language))
  }
}
