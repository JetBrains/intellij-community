// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation

import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.EvaluableFeature
import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.evaluable.common.CommonActionsInvoker
import com.intellij.cce.evaluation.step.*
import com.intellij.cce.interpreter.ActionsInvoker
import com.intellij.cce.interpreter.FeatureInvoker
import com.intellij.cce.interpreter.InvokersFactory
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

class BackgroundStepFactory(
  private val feature: EvaluableFeature<EvaluationStrategy>,
  private val config: Config,
  private val project: Project,
  private val inputWorkspacePaths: List<String>?,
  private val evaluationRootInfo: EvaluationRootInfo
) : StepFactory {

  private val invokersFactory = object : InvokersFactory {
    override fun createActionsInvoker(): ActionsInvoker = CommonActionsInvoker(project)
    override fun createFeatureInvoker(): FeatureInvoker = feature.getFeatureInvoker(project, Language.resolve(config.language), config.strategy)
  }

  override fun generateActionsStep(): EvaluationStep {
    return ActionsGenerationStep(config, config.language, evaluationRootInfo,
                                 project, feature.getGenerateActionsProcessor(config.strategy), feature.name)
  }

  override fun interpretActionsStep(): EvaluationStep =
    ActionsInterpretationStep(config, config.language, invokersFactory, project)

  override fun generateReportStep(): EvaluationStep =
    ReportGenerationStep(inputWorkspacePaths?.map { EvaluationWorkspace.open(it) },
                         config.reports.sessionsFilters, config.reports.comparisonFilters, project, feature)

  override fun interpretActionsOnNewWorkspaceStep(): EvaluationStep =
    ActionsInterpretationOnNewWorkspaceStep(config, invokersFactory, project)

  override fun reorderElements(): EvaluationStep =
    ReorderElementsStep(config, project)

  override fun setupStatsCollectorStep(): EvaluationStep? =
    if ((config.interpret.saveLogs || config.interpret.saveFeatures || config.interpret.experimentGroup != null)
        && !ApplicationManager.getApplication().isUnitTestMode
        && SetupStatsCollectorStep.isStatsCollectorEnabled())
      SetupStatsCollectorStep(config.interpret.experimentGroup, config.interpret.logLocationAndItemText)
    else null

  override fun setupSdkStep(): EvaluationStep? = SetupSdkStep.forLanguage(project, Language.resolve(config.language))

  override fun checkSdkConfiguredStep(): EvaluationStep = CheckProjectSdkStep(project, config.language)

  override fun finishEvaluationStep(): FinishEvaluationStep = HeadlessFinishEvaluationStep()

  override fun featureSpecificSteps(): List<EvaluationStep> =
    feature.getEvaluationSteps(Language.resolve(config.language), config.strategy)
}
