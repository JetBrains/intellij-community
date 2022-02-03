package com.intellij.completion.ml.features

import com.intellij.codeInsight.lookup.impl.LookupUsageTracker
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension

internal class MLRankingCompletionCollectorExtension : FeatureUsageCollectorExtension {
  override fun getGroupId(): String {
    return LookupUsageTracker.GROUP_ID
  }

  override fun getEventId(): String {
    return LookupUsageTracker.FINISHED_EVENT_ID
  }

  override fun getExtensionFields(): List<EventField<*>> {
    return listOf<EventField<*>>(totalMlTime, mlUsed, EventFields.Version)
  }

  companion object {
    val totalMlTime =  EventFields.Long("total_ml_time")
    val mlUsed =  EventFields.Boolean("ml_used")
  }
}
