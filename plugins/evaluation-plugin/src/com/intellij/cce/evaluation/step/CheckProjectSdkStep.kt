// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation.step

import com.intellij.cce.core.Language
import com.intellij.cce.evaluation.ForegroundEvaluationStep
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager

class CheckProjectSdkStep(private val project: Project, private val language: String) : ForegroundEvaluationStep {
  override val name: String = "Check Project SDK"
  override val description: String = "Checks that project SDK was configured properly"

  override fun start(workspace: EvaluationWorkspace): EvaluationWorkspace? {
    val resolvedLanguage = Language.resolve(language)
    if (!resolvedLanguage.needSdk || resolvedLanguage == Language.DART) return workspace

    val sdk = if (resolvedLanguage == Language.RUBY) {
      val module = ModuleManager.getInstance(project).modules[0]
      ModuleRootManager.getInstance(module).sdk
    }
    else {
      ProjectRootManager.getInstance(project).projectSdk
    }
    if (sdk == null) {
      println("Project SDK not found. Evaluation cannot be fair.")
      println("Configure project sdk and start again.")
      return null
    }

    return workspace
  }
}