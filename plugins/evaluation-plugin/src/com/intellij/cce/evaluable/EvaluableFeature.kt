// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable

import com.intellij.cce.core.Language
import com.intellij.cce.core.Session
import com.intellij.cce.evaluation.EvaluationEnvironment
import com.intellij.cce.evaluation.EvaluationStep
import com.intellij.cce.metric.Metric
import com.intellij.cce.report.FileReportGenerator
import com.intellij.cce.report.GeneratorDirectories
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace
import com.intellij.openapi.extensions.ExtensionPointName

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
   * @return initialized environment which will be used during evaluation
   */
  fun prepareEnvironment(config: Config, outputWorkspace: EvaluationWorkspace): EvaluationEnvironment

  /**
   * how to render the results of evaluation
   */
  fun getFileReportGenerator(filterName: String,
                             comparisonFilterName: String,
                             inputWorkspaces: List<EvaluationWorkspace>,
                             dirs: GeneratorDirectories): FileReportGenerator

  /**
   * which metrics to calculate and show in reports
   */
  fun getMetrics(sessions: List<Session>, language: Language): List<Metric> = getMetrics(sessions)

  fun getMetrics(sessions: List<Session>): List<Metric> = getMetrics()

  fun getMetrics(): List<Metric> = emptyList()


  /**
   * additional steps to set up evaluation
   */
  fun getEvaluationSteps(config: Config): List<EvaluationStep>


  /**
   * additional steps to set up evaluation before environment is initialized
   */
  fun getPreliminaryEvaluationSteps(): List<EvaluationStep>

  companion object {
    private val EP_NAME = ExtensionPointName.create<EvaluableFeature<EvaluationStrategy>>("com.intellij.cce.evaluableFeature")

    fun forFeature(featureName: String): EvaluableFeature<EvaluationStrategy>? {
      return EP_NAME.extensionList.singleOrNull { it.name == featureName }
    }
  }
}
