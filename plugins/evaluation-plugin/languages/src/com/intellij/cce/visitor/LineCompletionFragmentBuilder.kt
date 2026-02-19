// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.visitor

import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.golf.CompletionGolfMode
import com.intellij.openapi.project.Project

class LineCompletionFragmentBuilder(
  project: Project,
  language: Language?,
  private val featureName: String,
  private val completionGolfMode: CompletionGolfMode,
  private val fallbackToDefaultIfNotFound: Boolean = true,
) : CodeFragmentFromPsiBuilder(project, language) {
  override fun getVisitors(): List<LineCompletionEvaluationVisitor> {
    val knownVisitors = LineCompletionVisitorFactory.EP_NAME.extensionList
      .filter { it.language == language }.takeIf { it.isNotEmpty() }
      ?.map { it.createVisitor(featureName, completionGolfMode) }
    if (knownVisitors != null) return knownVisitors
    if (fallbackToDefaultIfNotFound) return listOf(LineCompletionAllEvaluationVisitor.Default(featureName, language ?: Language.ANOTHER))

    val registeredLanguages = LineCompletionVisitorFactory.EP_NAME.extensionList.map { it.language.displayName }
    throw IllegalStateException("No known visitors found for $language and $featureName. Registered languages: $registeredLanguages")
  }
}
