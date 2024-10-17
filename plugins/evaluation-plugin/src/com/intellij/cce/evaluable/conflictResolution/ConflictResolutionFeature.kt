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
import com.intellij.cce.workspace.storages.FeaturesStorage
import com.intellij.cce.workspace.storages.FullLineLogsStorage

class ConflictResolutionFeature : StandaloneFeature<ConflictResolutionStrategy>("conflict-resolution") {
  override fun getStrategySerializer(): StrategySerializer<ConflictResolutionStrategy> = ConflictResolutionStrategy.Serializer()

  override fun prepareEnvironment(config: Config): SimpleFileEnvironment = ConflictEnvironment(
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
    featuresStorages: List<FeaturesStorage>,
    fullLineStorages: List<FullLineLogsStorage>,
    dirs: GeneratorDirectories
  ): FileReportGenerator = ConflictResolutionReportGenerator(
    filterName,
    comparisonFilterName,
    featuresStorages,
    dirs
  )
}