package com.intellij.cce.evaluable.conflictResolution

import com.intellij.cce.actions.DatasetRef
import com.intellij.cce.evaluable.StandaloneFeature
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.evaluation.SimpleFileEnvironment
import com.intellij.cce.metric.Metric
import com.intellij.cce.metric.PrecisionMetric
import com.intellij.cce.metric.SessionsCountMetric
import com.intellij.cce.report.ConflictResolutionReportGenerator
import com.intellij.cce.report.FileReportGenerator
import com.intellij.cce.report.GeneratorDirectories
import com.intellij.cce.workspace.Config
import com.intellij.cce.workspace.EvaluationWorkspace

class ConflictResolutionFeature : StandaloneFeature<ConflictResolutionStrategy>("conflict-resolution") {
  override fun getStrategySerializer(): StrategySerializer<ConflictResolutionStrategy> = ConflictResolutionStrategy.Serializer()

  override fun prepareEnvironment(config: Config, outputWorkspace: EvaluationWorkspace): SimpleFileEnvironment = ConflictEnvironment(
    DatasetRef.parse(config.fileDataset!!.url),
    TheirConflictResolver()
  )

  override fun getMetrics(): List<Metric> = listOf(
    SessionsCountMetric(),
    PrecisionMetric()
  )

  override fun getFileReportGenerator(
    filterName: String,
    comparisonFilterName: String,
    inputWorkpaces: List<EvaluationWorkspace>,
    dirs: GeneratorDirectories
  ): FileReportGenerator = ConflictResolutionReportGenerator(
    filterName,
    comparisonFilterName,
    inputWorkpaces.map { it.featuresStorage },
    dirs
  )
}