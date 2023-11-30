// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluation

interface StepFactory {
  fun generateActionsStep(): EvaluationStep
  fun interpretActionsStep(): EvaluationStep
  fun interpretActionsOnNewWorkspaceStep(): EvaluationStep
  fun reorderElements(): EvaluationStep
  fun generateReportStep(): EvaluationStep
  fun setupStatsCollectorStep(): EvaluationStep?
  fun setupSdkStep(): EvaluationStep?
  fun checkSdkConfiguredStep(): EvaluationStep
  fun finishEvaluationStep(): FinishEvaluationStep
  fun featureSpecificSteps(): List<EvaluationStep>
}