package com.intellij.cce.evaluation

import com.intellij.cce.core.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

abstract class SetupSdkStep : EvaluationStep {
  companion object {
    private val EP_NAME = ExtensionPointName.create<SetupSdkStep>("com.intellij.cce.setupSdkStep")

    fun forLanguage(project: Project, language: Language): SetupSdkStep? {
      return EP_NAME.getExtensionList(project).singleOrNull { it.isApplicable(language) }
    }
  }

  abstract fun isApplicable(language: Language): Boolean
}