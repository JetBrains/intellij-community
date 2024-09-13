// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation.step

import com.intellij.cce.evaluation.TwoWorkspaceHandler
import com.intellij.cce.util.Progress
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace

abstract class CreateWorkspaceStep(
  private val config: Config,
  private val handler: TwoWorkspaceHandler
) : BackgroundEvaluationStep {

  override fun runInBackground(workspace: EvaluationWorkspace, progress: Progress): EvaluationWorkspace {
    val newWorkspace = EvaluationWorkspace.create(config, SetupStatsCollectorStep.statsCollectorLogsDirectory)
    handler.invoke(workspace, newWorkspace, progress)
    return newWorkspace
  }
}