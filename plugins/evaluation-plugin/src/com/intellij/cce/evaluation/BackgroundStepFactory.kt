// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation

import com.intellij.cce.actions.DatasetContext
import com.intellij.cce.evaluable.EvaluableFeature
import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.evaluation.step.*
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.application.ApplicationManager

class BackgroundStepFactory(
  private val feature: EvaluableFeature<EvaluationStrategy>,
  private val config: Config,
  private val environment: EvaluationEnvironment,
  private val inputWorkspacePaths: List<String>?,
  private val datasetContext: DatasetContext
) : StepFactory {

  override fun generateActionsStep(): EvaluationStep = DatasetPreparationStep(environment.dataset, datasetContext)

  override fun interpretActionsStep(): EvaluationStep =
    ActionsInterpretationStep(config, environment.dataset, datasetContext, newWorkspace = false)

  override fun generateReportStep(): EvaluationStep =
    ReportGenerationStep(inputWorkspacePaths?.map { EvaluationWorkspace.open(it, SetupStatsCollectorStep.statsCollectorLogsDirectory) },
                         config.reports.sessionsFilters, config.reports.comparisonFilters, feature)

  override fun interpretActionsOnNewWorkspaceStep(): EvaluationStep =
    ActionsInterpretationStep(config, environment.dataset, datasetContext, newWorkspace = true)

  override fun reorderElements(): EvaluationStep =
    ReorderElementsStep(config)

  override fun setupStatsCollectorStep(): EvaluationStep? =
    if ((config.interpret.saveLogs || config.interpret.saveFeatures || config.interpret.experimentGroup != null)
        && !ApplicationManager.getApplication().isUnitTestMode
        && SetupStatsCollectorStep.isStatsCollectorEnabled())
      SetupStatsCollectorStep(config.interpret.experimentGroup, config.interpret.logLocationAndItemText)
    else null

  override fun setupSdkStep(): EvaluationStep? = environment.dataset.setupSdk

  override fun checkSdkConfiguredStep(): EvaluationStep? = environment.dataset.checkSdk

  override fun finishEvaluationStep(): FinishEvaluationStep = HeadlessFinishEvaluationStep()

  override fun featureSpecificSteps(): List<EvaluationStep> = feature.getEvaluationSteps(config)

  override fun featureSpecificPreliminarySteps(): List<EvaluationStep> = feature.getPreliminaryEvaluationSteps()
}
