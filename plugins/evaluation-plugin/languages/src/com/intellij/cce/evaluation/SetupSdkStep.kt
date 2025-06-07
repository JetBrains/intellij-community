// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation

import com.intellij.cce.core.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface SetupSdkStepFactory {
  companion object {
    private val EP_NAME = ExtensionPointName.create<SetupSdkStepFactory>("com.intellij.cce.setupSdkStep")
    fun forLanguage(project: Project, language: Language): SetupSdkStepFactory? {
      return EP_NAME.getExtensionList(project).firstOrNull {
        it.isApplicable(language)
      }
    }
  }

  fun isApplicable(language: Language): Boolean

  fun steps(preferences: SetupSdkPreferences): List<EvaluationStep>
}

abstract class SetupSdkStep : SetupSdkStepFactory, ForegroundEvaluationStep {
  override fun steps(preferences: SetupSdkPreferences): List<EvaluationStep> = listOf(this)
}

/**
 * @property resolveDeps Indicates whether setup should try to resolve dependencies
 * @property projectLocal Should prefer project-local installation if applicable (i.e. use local .venv for python)
 * @property cacheDir Path to a directory which can be used by setup steps to store caches. No need to make anything with caches if null
 */
data class SetupSdkPreferences(
  val resolveDeps: Boolean,
  val projectLocal: Boolean = resolveDeps,
  val cacheDir: String? = null
)
