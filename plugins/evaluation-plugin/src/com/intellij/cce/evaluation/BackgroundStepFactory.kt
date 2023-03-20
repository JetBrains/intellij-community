package com.intellij.cce.evaluation

import com.intellij.cce.core.Language
import com.intellij.cce.evaluation.step.*
import com.intellij.cce.interpreter.CompletionInvoker
import com.intellij.cce.interpreter.CompletionInvokerImpl
import com.intellij.cce.interpreter.DelegationCompletionInvoker
import com.intellij.cce.metric.SuggestionsComparator
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project

class BackgroundStepFactory(
  private val config: Config,
  private val project: Project,
  private val isHeadless: Boolean,
  private val inputWorkspacePaths: List<String>?,
  private val evaluationRootInfo: EvaluationRootInfo
) : StepFactory {

  private var completionInvoker: CompletionInvoker = DelegationCompletionInvoker(
    CompletionInvokerImpl(project, Language.resolve(config.language),
                          config.interpret.completionType, config.interpret.emulationSettings, config.interpret.completionGolfSettings),
    project)

  override fun generateActionsStep(): EvaluationStep =
    ActionsGenerationStep(config.actions, config.language, evaluationRootInfo, project, isHeadless)

  override fun interpretActionsStep(): EvaluationStep =
    ActionsInterpretationStep(config.interpret, config.language, completionInvoker, project, isHeadless)

  override fun generateReportStep(): EvaluationStep =
    ReportGenerationStep(inputWorkspacePaths?.map { EvaluationWorkspace.open(it) },
                         config.reports.sessionsFilters, config.reports.comparisonFilters, project, isHeadless)

  override fun interpretActionsOnNewWorkspaceStep(): EvaluationStep =
    ActionsInterpretationOnNewWorkspaceStep(config, completionInvoker, project, isHeadless)

  override fun reorderElements(): EvaluationStep =
    ReorderElementsStep(config, project, isHeadless)

  override fun highlightTokensInIdeStep(): EvaluationStep = HighlightingTokensInIdeStep(
    SuggestionsComparator.create(Language.resolve(config.language), config.interpret.completionType), project, isHeadless)

  override fun setupStatsCollectorStep(): EvaluationStep? =
    if ((config.interpret.saveLogs || config.interpret.saveFeatures || config.interpret.experimentGroup != null)
        && !ApplicationManager.getApplication().isUnitTestMode
        && SetupStatsCollectorStep.isStatsCollectorEnabled())
      SetupStatsCollectorStep(project, config.interpret.experimentGroup,
                              config.interpret.logLocationAndItemText, isHeadless)
    else null

  override fun setupFullLineStep(): EvaluationStep = SetupFullLineStep()

  override fun setupCompletionStep(): EvaluationStep = SetupCompletionStep(config.language, config.interpret.completionType)

  override fun setupSdkStep(): EvaluationStep? = SetupSdkStep.forLanguage(project, Language.resolve(config.language))

  override fun checkSdkConfiguredStep(): EvaluationStep = CheckProjectSdkStep(project, config.language)

  override fun finishEvaluationStep(): EvaluationStep =
    if (isHeadless) HeadlessFinishEvaluationStep() else UIFinishEvaluationStep(project)
}
