// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.filters

import com.intellij.internal.statistic.config.bean.EventLogBucketRange
import com.intellij.internal.statistic.eventLog.LogEvent

class LogEventBucketsFilter(val buckets: List<EventLogBucketRange>) : LogEventFilter {
  override fun accepts(event: LogEvent): Boolean {
    val bucket = event.bucket.toIntOrNull() ?: return false
    return buckets.any { it.contains(bucket) }
  }
}