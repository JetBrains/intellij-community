// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.features

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.LookupUsageDescriptor
import com.intellij.completion.ml.features.MLRankingCompletionCollectorExtension.Companion.mlUsed
import com.intellij.completion.ml.features.MLRankingCompletionCollectorExtension.Companion.totalMlTime
import com.intellij.completion.ml.storage.LookupStorage
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair

class MLRankingLookupUsageDescriptor : LookupUsageDescriptor {
  override fun getExtensionKey(): String = "ml"

  override fun getAdditionalUsageData(lookup: Lookup): List<EventPair<*>> {
    val data = mutableListOf<EventPair<*>>()
    if (lookup.isCompletion && lookup is LookupImpl) {
      val storage = LookupStorage.get(lookup)
      if (storage != null) {
        data.add(totalMlTime.with(storage.performanceTracker.totalMLTimeContribution()))

        data.add(mlUsed.with(storage.mlUsed()))
        data.add(EventFields.Version.with( storage.model?.version() ?: "unknown"))
      }
    }
    return data
  }
}