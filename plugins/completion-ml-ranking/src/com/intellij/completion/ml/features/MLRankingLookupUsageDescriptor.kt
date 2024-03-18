// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.features

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.LookupResultDescriptor
import com.intellij.codeInsight.lookup.impl.LookupUsageDescriptor
import com.intellij.codeInsight.lookup.impl.LookupUsageTracker
import com.intellij.completion.ml.storage.LookupStorage
import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsageCollectorExtension

private val TOTAL_ML_TIME = EventFields.Long("total_ml_time")
private val ML_USED = EventFields.Boolean("ml_used")

internal class MLRankingLookupUsageDescriptor : LookupUsageDescriptor {
  override fun getExtensionKey(): String = "ml"

  override fun getAdditionalUsageData(lookupResultDescriptor: LookupResultDescriptor): List<EventPair<*>> {
    val lookup = lookupResultDescriptor.lookup
    val data = mutableListOf<EventPair<*>>()
    if (lookup.isCompletion && lookup is LookupImpl) {
      val storage = LookupStorage.get(lookup)
      if (storage != null) {
        data.add(TOTAL_ML_TIME.with(storage.performanceTracker.totalMLTimeContribution()))

        data.add(ML_USED.with(storage.mlUsed()))
        data.add(EventFields.Version.with(storage.model?.version() ?: "unknown"))
      }
    }
    return data
  }

  internal class MLRankingCompletionCollectorExtension : FeatureUsageCollectorExtension {
    override fun getGroupId(): String {
      return LookupUsageTracker.GROUP_ID
    }

    override fun getEventId(): String {
      return LookupUsageTracker.FINISHED_EVENT_ID
    }

    override fun getExtensionFields(): List<EventField<*>> {
      return listOf<EventField<*>>(TOTAL_ML_TIME, ML_USED, EventFields.Version)
    }
  }
}