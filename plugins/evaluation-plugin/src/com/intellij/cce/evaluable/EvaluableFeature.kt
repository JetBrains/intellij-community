// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable

import com.intellij.cce.core.Language
import com.intellij.cce.evaluation.EvaluationStep
import com.intellij.cce.interpreter.ActionsInvoker
import com.intellij.cce.metric.*
import com.intellij.cce.processor.GenerateActionsProcessor
import com.intellij.cce.report.BasicFileReportGenerator
import com.intellij.cce.report.FileReportGenerator
import com.intellij.cce.report.GeneratorDirectories
import com.intellij.cce.workspace.storages.FeaturesStorage
import com.intellij.cce.workspace.storages.FullLineLogsStorage
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface EvaluableFeature<T : EvaluationStrategy> {
  companion object {
    private val EP_NAME = ExtensionPointName.create<EvaluableFeature<EvaluationStrategy>>("com.intellij.cce.evaluableFeature")

    fun forFeature(featureName: String): EvaluableFeature<EvaluationStrategy>? {
      return EP_NAME.extensionList.singleOrNull { it.name == featureName }
    }

  }

  val name: String

  fun getStrategySerializer(): StrategySerializer<T>

  fun getGenerateActionsProcessor(strategy: T) : GenerateActionsProcessor

  fun getActionsInvoker(project: Project, language: Language, strategy: T): ActionsInvoker

  fun getFileReportGenerator(suggestionsComparators: List<SuggestionsComparator>,
                             filterName: String,
                             comparisonFilterName: String,
                             featuresStorages: List<FeaturesStorage>,
                             fullLineStorages: List<FullLineLogsStorage>,
                             dirs: GeneratorDirectories) : FileReportGenerator =
    BasicFileReportGenerator(suggestionsComparators, filterName, comparisonFilterName, featuresStorages, dirs)

  fun getMetrics(): List<Metric> = listOf(
    RecallAtMetric(1),
    RecallAtMetric(5),
    RecallMetric(),
    Precision(),
    MeanRankMetric(),
    MeanLatencyMetric(),
    MaxLatencyMetric(),
    SessionsCountMetric(),
    SuggestionsCountMetric()
  )

  fun getSuggestionsComparator(language: Language): SuggestionsComparator = SuggestionsComparator.create(language)

  fun getEvaluationSteps(language: Language, strategy: T): List<EvaluationStep> = emptyList()
}
