// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable

import com.intellij.cce.core.Language
import com.intellij.cce.evaluation.EvaluationStep
import com.intellij.cce.interpreter.FeatureInvoker
import com.intellij.cce.metric.Metric
import com.intellij.cce.metric.SuggestionsComparator
import com.intellij.cce.processor.GenerateActionsProcessor
import com.intellij.cce.report.FileReportGenerator
import com.intellij.cce.report.GeneratorDirectories
import com.intellij.cce.workspace.storages.FeaturesStorage
import com.intellij.cce.workspace.storages.FullLineLogsStorage
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface EvaluableFeature<T : EvaluationStrategy> {
  val name: String

  fun getStrategySerializer(): StrategySerializer<T>

  fun getGenerateActionsProcessor(strategy: T): GenerateActionsProcessor

  fun getFeatureInvoker(project: Project, language: Language, strategy: T): FeatureInvoker

  fun getFileReportGenerator(suggestionsComparators: List<SuggestionsComparator>,
                             filterName: String,
                             comparisonFilterName: String,
                             featuresStorages: List<FeaturesStorage>,
                             fullLineStorages: List<FullLineLogsStorage>,
                             dirs: GeneratorDirectories): FileReportGenerator

  fun getMetrics(): List<Metric>

  fun getSuggestionsComparator(language: Language): SuggestionsComparator

  fun getEvaluationSteps(language: Language, strategy: T): List<EvaluationStep>

  companion object {
    private val EP_NAME = ExtensionPointName.create<EvaluableFeature<EvaluationStrategy>>("com.intellij.cce.evaluableFeature")

    fun forFeature(featureName: String): EvaluableFeature<EvaluationStrategy>? {
      return EP_NAME.extensionList.singleOrNull { it.name == featureName }
    }
  }
}
