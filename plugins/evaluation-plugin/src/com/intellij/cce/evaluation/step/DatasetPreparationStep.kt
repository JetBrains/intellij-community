package com.intellij.cce.evaluation.step

import com.intellij.cce.actions.DatasetContext
import com.intellij.cce.actions.EvaluationDataset
import com.intellij.cce.util.Progress
import com.intellij.cce.workspace.EvaluationWorkspace

class DatasetPreparationStep(
  private val dataset: EvaluationDataset,
  private val datasetContext: DatasetContext,
) : BackgroundEvaluationStep {
  override val name: String = "Preparing dataset"

  override val description: String = dataset.preparationDescription

  override fun runInBackground(workspace: EvaluationWorkspace, progress: Progress): EvaluationWorkspace {
    dataset.prepare(datasetContext, progress)
    return workspace
  }
}