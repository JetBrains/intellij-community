package com.intellij.cce.evaluation.step

import com.intellij.cce.actions.DatasetContext
import com.intellij.cce.evaluation.EvaluationEnvironment
import com.intellij.cce.util.Progress
import com.intellij.cce.workspace.EvaluationWorkspace

class DatasetPreparationStep(
  private val environment: EvaluationEnvironment,
  private val datasetContext: DatasetContext,
) : BackgroundEvaluationStep {
  override val name: String = "Preparing dataset"

  override val description: String = environment.preparationDescription

  override fun runInBackground(workspace: EvaluationWorkspace, progress: Progress): EvaluationWorkspace {
    environment.prepare(datasetContext, progress)
    return workspace
  }
}