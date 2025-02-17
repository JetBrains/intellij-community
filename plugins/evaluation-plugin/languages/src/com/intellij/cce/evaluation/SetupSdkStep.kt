// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation

import com.intellij.cce.core.Language
import com.intellij.cce.execution.ExecutionMode
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

abstract class SetupSdkStep : ForegroundEvaluationStep {
  companion object {
    private val EP_NAME = ExtensionPointName.create<SetupSdkStep>("com.intellij.cce.setupSdkStep")
    fun forLanguage(project: Project, language: Language, executionMode: ExecutionMode): SetupSdkStep? {
      // LOCAL execution mode is default
      return EP_NAME.getExtensionList(project).firstOrNull {
        it.isApplicable(language) &&
        it.isDockerBased == (executionMode == ExecutionMode.DOCKER)
      }
    }
  }

  open val isDockerBased: Boolean = false
  abstract fun isApplicable(language: Language): Boolean
}