package com.intellij.cce.evaluation

import com.intellij.cce.core.Language
import com.intellij.cce.evaluation.step.*
import com.intellij.cce.evaluable.EvaluableFeature
import com.intellij.cce.evaluable.EvaluationStrategy
import com.intellij.cce.interpreter.ActionsInvoker
import com.intellij.cce.interpreter.DelegationActionsInvoker
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

  private val invoker: ActionsInvoker = DelegationActionsInvoker(
    feature.getActionsInvoker(project, Language.resolve(config.language), config.strategy), project
  )

  override fun generateActionsStep(): EvaluationStep {
    return ActionsGenerationStep(config, config.language, evaluationRootInfo,
                                 project, feature.getGenerateActionsProcessor(config.strategy), feature.name)
  }

  override fun interpretActionsStep(): EvaluationStep =
    ActionsInterpretationStep(config.interpret, config.language, invoker, project)

  override fun generateReportStep(): EvaluationStep =
    ReportGenerationStep(inputWorkspacePaths?.map { EvaluationWorkspace.open(it) },
                         config.reports.sessionsFilters, config.reports.comparisonFilters, project, feature)

  override fun interpretActionsOnNewWorkspaceStep(): EvaluationStep =
    ActionsInterpretationOnNewWorkspaceStep(config, invoker, project)

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

  override fun finishEvaluationStep(): EvaluationStep = HeadlessFinishEvaluationStep()

  override fun featureSpecificSteps(): List<EvaluationStep> =
    feature.getEvaluationSteps(Language.resolve(config.language), config.strategy)
}
