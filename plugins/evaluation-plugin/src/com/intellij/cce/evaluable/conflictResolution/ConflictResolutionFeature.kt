package com.intellij.cce.evaluable.conflictResolution

import com.intellij.cce.actions.DatasetRef
import com.intellij.cce.actions.EvaluationDataset
import com.intellij.cce.evaluable.StandaloneFeature
import com.intellij.cce.evaluable.StrategySerializer
import com.intellij.cce.metric.Metric
import com.intellij.cce.metric.PrecisionMetric
import com.intellij.cce.metric.SessionsCountMetric
import com.intellij.cce.workspace.Config

class ConflictResolutionFeature : StandaloneFeature<ConflictResolutionStrategy>("conflict-resolution") {
  override fun getStrategySerializer(): StrategySerializer<ConflictResolutionStrategy> = ConflictResolutionStrategy.Serializer()

  override fun getDataset(config: Config): EvaluationDataset = ConflictDataset(
    DatasetRef.parse(config.conflictDataset!!.url),
    TheirConflictResolver()
  )

  override fun getMetrics(): List<Metric> = listOf(
    SessionsCountMetric(),
    PrecisionMetric()
  )
}