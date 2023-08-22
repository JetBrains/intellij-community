// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable

import com.intellij.cce.core.Language
import com.intellij.cce.evaluation.EvaluationStep
import com.intellij.cce.interpreter.FeatureInvoker
import com.intellij.cce.metric.Metric
import com.intellij.cce.processor.GenerateActionsProcessor
import com.intellij.cce.report.FileReportGenerator
import com.intellij.cce.report.GeneratorDirectories
import com.intellij.cce.workspace.storages.FeaturesStorage
import com.intellij.cce.workspace.storages.FullLineLogsStorage
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * Represents a feature that can be evaluated in IDE.
 * To define a new feature, you can inherit from {@link EvaluableFeatureBase} which implements some methods by default.
 */
interface EvaluableFeature<T : EvaluationStrategy> {
  val name: String

  /**
   * how to serialize/deserialize a strategy (list of parameters) for evaluation of the feature
   */
  fun getStrategySerializer(): StrategySerializer<T>

  /**
   * how to prepare the context before the feature invocation
   */
  fun getGenerateActionsProcessor(strategy: T): GenerateActionsProcessor

  /**
   * how to call the feature
   */
  fun getFeatureInvoker(project: Project, language: Language, strategy: T): FeatureInvoker

  /**
   * how to render the results of evaluation
   */
  fun getFileReportGenerator(filterName: String,
                             comparisonFilterName: String,
                             featuresStorages: List<FeaturesStorage>,
                             fullLineStorages: List<FullLineLogsStorage>,
                             dirs: GeneratorDirectories): FileReportGenerator

  /**
   * which metrics to calculate and show in reports
   */
  fun getMetrics(): List<Metric>

  /**
   * additional steps to set up evaluation
   */
  fun getEvaluationSteps(language: Language, strategy: T): List<EvaluationStep>

  companion object {
    private val EP_NAME = ExtensionPointName.create<EvaluableFeature<EvaluationStrategy>>("com.intellij.cce.evaluableFeature")

    fun forFeature(featureName: String): EvaluableFeature<EvaluationStrategy>? {
      return EP_NAME.extensionList.singleOrNull { it.name == featureName }
    }
  }
}
