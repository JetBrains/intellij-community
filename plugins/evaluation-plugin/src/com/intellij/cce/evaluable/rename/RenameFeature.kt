// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.rename


import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.EvaluableFeatureBase
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.evaluation.EvaluationStep
import com.intellij.cce.interpreter.FeatureInvoker
import com.intellij.cce.metric.EditSimilarity
import com.intellij.cce.metric.Metric
import com.intellij.cce.metric.createBaseCompletionMetrics
import com.intellij.cce.processor.GenerateActionsProcessor
import com.intellij.openapi.project.Project


class RenameFeature : EvaluableFeatureBase<RenameStrategy>("rename") {

  override fun getGenerateActionsProcessor(strategy: RenameStrategy): GenerateActionsProcessor =
    RenameGenerateActionsProcessor(strategy)

  override fun getFeatureInvoker(project: Project, language: Language, strategy: RenameStrategy): FeatureInvoker =
    RenameInvoker(project, language, strategy)

  override fun getStrategySerializer(): StrategySerializer<RenameStrategy> = RenameStrategySerializer()

  override fun getMetrics(): List<Metric> = listOf(EditSimilarity(showByDefault = true)) + createBaseCompletionMetrics(showByDefault = true)

  override fun getEvaluationSteps(language: Language, strategy: RenameStrategy): List<EvaluationStep> = emptyList()
}
