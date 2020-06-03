// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.service.fus.StatisticsWhitelistConditions
import com.intellij.internal.statistic.config.bean.EventLogBucketRange

interface LogEventFilter {
  fun accepts(event: LogEvent) : Boolean
}

class LogEventWhitelistFilter(val whitelist: StatisticsWhitelistConditions) : LogEventFilter {
  override fun accepts(event: LogEvent): Boolean {
    return whitelist.accepts(event.group.id, event.group.version, event.build)
  }
}

class LogEventBucketsFilter(val buckets: List<EventLogBucketRange>) : LogEventFilter {
  override fun accepts(event: LogEvent): Boolean {
    val bucket = event.bucket.toIntOrNull() ?: return false
    return buckets.any { it.contains(bucket) }
  }
}

object LogEventSnapshotBuildFilter : LogEventFilter {
  override fun accepts(event: LogEvent): Boolean {
    val parts = EventLogBuild.fromString(event.build)?.components
    return parts != null && (parts.size != 2 || parts[1] != 0)
  }
}

class LogEventCompositeFilter(vararg val filters : LogEventFilter) : LogEventFilter {
  override fun accepts(event: LogEvent): Boolean {
    return filters.all { filter -> filter.accepts(event) }
  }
}

object LogEventTrueFilter : LogEventFilter {
  override fun accepts(event: LogEvent): Boolean {
    return true
  }
}

object LogEventFalseFilter : LogEventFilter {
  override fun accepts(event: LogEvent): Boolean {
    return false
  }
}