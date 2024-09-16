package com.intellij.cce.evaluable.testGeneration

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

class TestGenerationFeature : EvaluableFeatureBase<TestGenerationStrategy>("test-generation") {

  override fun getGenerateActionsProcessor(strategy: TestGenerationStrategy, project: Project): GenerateActionsProcessor = TestGenerationActionsProcessor()

  override fun getFeatureInvoker(project: Project, language: Language, strategy: TestGenerationStrategy): FeatureInvoker =
    TestGenerationInvoker(project, language, strategy)

  override fun getStrategySerializer(): StrategySerializer<TestGenerationStrategy> = object : StrategySerializer<TestGenerationStrategy> {
    override fun deserialize(map: Map<String, Any>, language: String): TestGenerationStrategy = TestGenerationStrategy()
    override fun serialize(src: TestGenerationStrategy, typeOfSrc: Type, context: JsonSerializationContext): JsonObject = JsonObject()
  }

  override fun getMetrics(): List<Metric> = listOf(SessionsCountMetric(), EmptyContextSessionRatio())

  override fun getEvaluationSteps(language: Language, strategy: TestGenerationStrategy): List<EvaluationStep> = emptyList()
}