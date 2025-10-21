package com.intellij.findUsagesMl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.platform.ml.logs.IntelliJFusEventRegister
import com.jetbrains.mlapi.logs.MLTreeLogger


internal object FindUsagesFileRankerFeatureCollector : CounterUsagesCollector() {
  const val recorderId: String = "ML"
  const val eventGroupId: String = "findUsages.fileRanking"
  const val fusEventName: String = "find_usages_file_ranking"

  private val GROUP = EventLogGroup(eventGroupId, 4, recorderId)

  val mlLogger = MLTreeLogger.withOneEvent(
    eventName = fusEventName,
    logsEventRegister = IntelliJFusEventRegister(GROUP),
    treeFeatures = listOf(FindUsagesFileRankerFeatures.extractFeatureDeclarations()),
    treeAnalysis = listOf(FindUsagesFileRankerAnalysisTargets.extractFeatureDeclarations())
  )

  override fun getGroup() = GROUP
}
