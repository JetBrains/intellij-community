package com.intellij.findUsagesMl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.platform.ml.logs.IntelliJFusEventRegister
import com.jetbrains.ml.tools.logs.MLTreeLoggers.withOneEvent


internal object FindUsagesFileRankerFeatureCollector : CounterUsagesCollector() {
  const val recorderId: String = "ML"
  const val eventGroupId: String = "findUsages.fileRanking"
  const val fusEventName: String = "find_usages_file_ranking"

  private val GROUP = EventLogGroup(eventGroupId, 1, recorderId)

  val mlLogger = withOneEvent(
    fusEventName = fusEventName,
    fusEventRegister = IntelliJFusEventRegister(GROUP),
    treeFeatures = FindUsagesFileRankerFeatures.declarations(),
    treeAnalysis = FindUsagesFileRankerAnalysisTargets.eventFields()
  )

  override fun getGroup() = GROUP
}
