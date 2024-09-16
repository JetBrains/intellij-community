// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.docGeneration

import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.intellij.cce.core.Language
import com.intellij.cce.evaluable.EvaluableFeatureBase
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.evaluation.EvaluationStep
import com.intellij.cce.interpreter.FeatureInvoker
import com.intellij.cce.metric.EmptyContextSessionRatio
import com.intellij.cce.metric.Metric
import com.intellij.cce.metric.SessionsCountMetric
import com.intellij.cce.processor.GenerateActionsProcessor
import com.intellij.openapi.project.Project
import java.lang.reflect.Type


class DocGenerationFeature : EvaluableFeatureBase<DocGenerationStrategy>("doc-generation") {

  override fun getGenerateActionsProcessor(strategy: DocGenerationStrategy, project: Project): GenerateActionsProcessor =
    DocGenerationActionsProcessor()

  override fun getFeatureInvoker(project: Project, language: Language, strategy: DocGenerationStrategy): FeatureInvoker =
    DocGenerationInvoker(project, language, strategy)

  override fun getStrategySerializer(): StrategySerializer<DocGenerationStrategy> = object : StrategySerializer<DocGenerationStrategy> {
    override fun deserialize(map: Map<String, Any>, language: String): DocGenerationStrategy = DocGenerationStrategy()
    override fun serialize(src: DocGenerationStrategy, typeOfSrc: Type, context: JsonSerializationContext): JsonObject = JsonObject()
  }

  override fun getMetrics(): List<Metric> = listOf(SessionsCountMetric(), EmptyContextSessionRatio())

  override fun getEvaluationSteps(language: Language, strategy: DocGenerationStrategy): List<EvaluationStep> = emptyList()
}