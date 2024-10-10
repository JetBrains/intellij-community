package com.intellij.cce.evaluable

import com.intellij.cce.evaluation.EvaluationStep
import com.intellij.cce.report.BasicFileReportGenerator
import com.intellij.cce.report.FileReportGenerator
import com.intellij.cce.report.GeneratorDirectories
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.storages.FeaturesStorage
import com.intellij.cce.workspace.storages.FullLineLogsStorage

abstract class StandaloneFeature<T : EvaluationStrategy>(
  override val name: String
) : EvaluableFeature<T> {

  override fun getPreliminaryEvaluationSteps(): List<EvaluationStep> = emptyList()

  override fun getEvaluationSteps(config: Config): List<EvaluationStep> = emptyList()

  override fun getFileReportGenerator(
    filterName: String,
    comparisonFilterName: String,
    featuresStorages: List<FeaturesStorage>,
    fullLineStorages: List<FullLineLogsStorage>,
    dirs: GeneratorDirectories
  ): FileReportGenerator = BasicFileReportGenerator(filterName, comparisonFilterName, featuresStorages, dirs)
}