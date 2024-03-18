// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.completion

import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.EvaluableFeatureBase
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.evaluation.EvaluationStep
import com.intellij.cce.evaluation.step.SetupCompletionStep
import com.intellij.cce.interpreter.FeatureInvoker
import com.intellij.cce.metric.Metric
import com.intellij.cce.metric.SessionsCountMetric
import com.intellij.cce.metric.createBaseCompletionMetrics
import com.intellij.cce.processor.GenerateActionsProcessor
import com.intellij.openapi.project.Project


class TokenCompletionFeature : EvaluableFeatureBase<CompletionStrategy>("token-completion") {

  override fun getGenerateActionsProcessor(strategy: CompletionStrategy): GenerateActionsProcessor =
    CompletionGenerateActionsProcessor(strategy)

  override fun getFeatureInvoker(project: Project, language: Language, strategy: CompletionStrategy): FeatureInvoker =
    CompletionActionsInvoker(project, language, strategy)

  override fun getMetrics(): List<Metric> = createBaseCompletionMetrics(showByDefault = true) + listOf(SessionsCountMetric())

  override fun getStrategySerializer(): StrategySerializer<CompletionStrategy> = CompletionStrategySerializer()

  override fun getEvaluationSteps(language: Language, strategy: CompletionStrategy): List<EvaluationStep> =
    listOf(SetupCompletionStep(language, strategy.completionType))
}