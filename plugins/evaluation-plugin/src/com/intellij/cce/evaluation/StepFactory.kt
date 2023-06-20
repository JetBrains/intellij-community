package com.intellij.cce.evaluation

interface StepFactory {
  fun generateActionsStep(): EvaluationStep
  fun interpretActionsStep(): EvaluationStep
  fun interpretActionsOnNewWorkspaceStep(): EvaluationStep
  fun reorderElements(): EvaluationStep
  fun generateReportStep(): EvaluationStep
  fun finishEvaluationStep(): EvaluationStep
  fun setupStatsCollectorStep(): EvaluationStep?
  fun setupSdkStep(): EvaluationStep?
  fun checkSdkConfiguredStep(): EvaluationStep
  fun featureSpecificSteps() : List<EvaluationStep>
}