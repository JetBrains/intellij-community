package com.intellij.cce.visitor

import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.golf.CompletionGolfMode
import com.intellij.openapi.project.Project

class CompletionGolfFragmentBuilder(project: Project,
                                    language: Language,
                                    private val featureName: String,
                                    private val completionGolfMode: CompletionGolfMode)
  : CodeFragmentFromPsiBuilder(project, language) {
  override fun getVisitors(): List<CompletionGolfEvaluationVisitor> {
    return CompletionGolfVisitorFactory.EP_NAME.extensions.toList()
      .filter { it.language == language }.takeIf { it.isNotEmpty() }
      ?.map { it.createVisitor(featureName, completionGolfMode) }
           ?: listOf(CompletionGolfAllEvaluationVisitor.Default(featureName, language))
  }
}
