// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.ActionsInterpretationHandler
import com.intellij.cce.interpreter.InvokersFactory
import com.intellij.cce.util.Progress
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.project.Project

class ActionsInterpretationStep(
  private val config: Config,
  private val language: String,
  private val invokersFactory: InvokersFactory,
  project: Project) : BackgroundEvaluationStep(project) {
  override val name: String = "Actions interpreting"

  override val description: String = "Interpretation of generated actions"

  override fun runInBackground(workspace: EvaluationWorkspace, progress: Progress): EvaluationWorkspace {
    ActionsInterpretationHandler(config, language, invokersFactory, project).invoke(workspace, workspace, progress)
    return workspace
  }
}