// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation.step

import com.intellij.cce.actions.DatasetContext
import com.intellij.cce.evaluation.ActionsInterpretationHandler
import com.intellij.cce.evaluation.EvaluationEnvironment
import com.intellij.cce.util.Progress
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace

class ActionsInterpretationStep(
  private val config: Config,
  private val environment: EvaluationEnvironment,
  private val datasetContext: DatasetContext,
  private val newWorkspace: Boolean
) : BackgroundEvaluationStep {
  override val name: String = "Actions interpreting"

  override val description: String = "Interpretation of generated actions"

  override fun runInBackground(workspace: EvaluationWorkspace, progress: Progress): EvaluationWorkspace {
    val resultWorkspace =
      if (newWorkspace) EvaluationWorkspace.create(config, SetupStatsCollectorStep.statsCollectorLogsDirectory)
      else workspace
    ActionsInterpretationHandler(config, datasetContext).invoke(environment, resultWorkspace, progress)
    return resultWorkspace
  }
}