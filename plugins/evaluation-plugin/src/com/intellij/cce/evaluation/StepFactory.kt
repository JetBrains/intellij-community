package com.intellij.cce.evaluation

interface StepFactory {
  fun generateActionsStep(): EvaluationStep
  fun interpretActionsStep(): EvaluationStep
  fun interpretActionsOnNewWorkspaceStep(): EvaluationStep
  fun reorderElements(): EvaluationStep
  fun highlightTokensInIdeStep(): EvaluationStep
  fun generateReportStep(): EvaluationStep
  fun finishEvaluationStep(): EvaluationStep

  fun setupCompletionStep(): EvaluationStep
  fun setupStatsCollectorStep(): EvaluationStep?
  fun setupFullLineStep(): EvaluationStep
  fun setupSdkStep(): EvaluationStep?
  fun checkSdkConfiguredStep(): EvaluationStep
}