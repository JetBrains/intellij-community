// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.golf

import com.intellij.cce.core.Language
import com.intellij.cce.core.Suggestion
import com.intellij.cce.core.SuggestionKind
import com.intellij.cce.evaluable.EvaluableFeatureBase
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.evaluation.EvaluationStep
import com.intellij.cce.evaluation.step.SetupCompletionStep
import com.intellij.cce.evaluation.step.SetupFullLineStep
import com.intellij.cce.interpreter.FeatureInvoker
import com.intellij.cce.metric.Metric
import com.intellij.cce.metric.SuggestionsComparator
import com.intellij.cce.metric.createCompletionGolfMetrics
import com.intellij.cce.processor.GenerateActionsProcessor
import com.intellij.cce.report.CompletionGolfFileReportGenerator
import com.intellij.cce.report.GeneratorDirectories
import com.intellij.cce.workspace.storages.FeaturesStorage
import com.intellij.cce.workspace.storages.FullLineLogsStorage
import com.intellij.openapi.project.Project


class LineCompletionGolfFeature : EvaluableFeatureBase<CompletionGolfStrategy>("line-completion-golf") {

  override fun getGenerateActionsProcessor(strategy: CompletionGolfStrategy): GenerateActionsProcessor = LineCompletionProcessor()

  override fun getFeatureInvoker(project: Project, language: Language, strategy: CompletionGolfStrategy): FeatureInvoker =
    LineCompletionActionsInvoker(project, language, strategy, false)

  override fun getStrategySerializer(): StrategySerializer<CompletionGolfStrategy> = LineCompletionStrategySerializer()

  override fun getFileReportGenerator(suggestionsComparators: List<SuggestionsComparator>,
                                      filterName: String,
                                      comparisonFilterName: String,
                                      featuresStorages: List<FeaturesStorage>,
                                      fullLineStorages: List<FullLineLogsStorage>,
                                      dirs: GeneratorDirectories): CompletionGolfFileReportGenerator {
    return CompletionGolfFileReportGenerator(filterName, comparisonFilterName, featuresStorages, fullLineStorages, dirs)
  }

  override fun getMetrics(): List<Metric> = createCompletionGolfMetrics() + super.getMetrics()

  override fun getSuggestionsComparator(language: Language): SuggestionsComparator = object : SuggestionsComparator {
    override fun accept(suggestion: Suggestion, expected: String): Boolean = suggestion.kind != SuggestionKind.ANY
  }

  override fun getEvaluationSteps(language: Language, strategy: CompletionGolfStrategy): List<EvaluationStep> =
    listOf(
      SetupCompletionStep(
        language = language.name,
        completionType = strategy.completionType,
        pathToZipModel = strategy.pathToZipModel
      ),
      SetupFullLineStep()
    )
}
