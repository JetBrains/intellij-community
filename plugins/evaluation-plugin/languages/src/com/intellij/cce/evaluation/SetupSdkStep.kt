// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation

import com.intellij.cce.core.Language
import com.intellij.cce.evaluation.data.ExecutionMode
import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

abstract class SetupSdkStep : ForegroundEvaluationStep {
  companion object {
    private val EP_NAME = ExtensionPointName.create<SetupSdkStep>("com.intellij.cce.setupSdkStep")
    fun forLanguage(project: Project, language: Language, strategy: EvaluationStrategy): SetupSdkStep? {
      return EP_NAME.getExtensionList(project).firstOrNull {
        it.isApplicable(language, strategy)
      }
    }
  }

  abstract fun isApplicable(language: Language): Boolean

  open fun isApplicable(language: Language, strategy: EvaluationStrategy): Boolean =
    strategy.executionMode == ExecutionMode.LOCAL && isApplicable(language)
}
