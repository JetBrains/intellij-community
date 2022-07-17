// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEventAction

interface StatisticsEventMergeStrategy {
  fun shouldMerge(lastEvent: LogEvent, newEvent: LogEvent): Boolean
}

class FilteredEventMergeStrategy(private val ignoredFields: Set<String>) : StatisticsEventMergeStrategy {
  override fun shouldMerge(lastEvent: LogEvent, newEvent: LogEvent): Boolean {
    if (lastEvent.session != newEvent.session) return false
    if (lastEvent.bucket != newEvent.bucket) return false
    if (lastEvent.build != newEvent.build) return false
    if (lastEvent.recorderVersion != newEvent.recorderVersion) return false
    if (lastEvent.group.id != newEvent.group.id) return false
    if (lastEvent.group.version != newEvent.group.version) return false
    if (!shouldMergeEvents(lastEvent.event, newEvent.event)) return false
    return true
  }

  private fun shouldMergeEvents(lastEvent: LogEventAction, newEvent: LogEventAction): Boolean {
    if (lastEvent.state || newEvent.state) return false

    if (lastEvent.id != newEvent.id) return false
    if (lastEvent.data.size != newEvent.data.size) return false

    for (datum in lastEvent.data) {
      val key = datum.key
      if (!ignoredFields.contains(key)) {
        val value = newEvent.data[key]
        if (value == null || value != datum.value) return false
      }
    }
    return true
  }
}