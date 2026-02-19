// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.filters

import com.jetbrains.fus.reporting.model.config.v4.ConfigurationBucketRange
import com.jetbrains.fus.reporting.model.lion3.LogEvent

class LogEventBucketsFilter(val buckets: List<ConfigurationBucketRange>) : LogEventFilter {
  override fun accepts(event: LogEvent): Boolean {
    val bucket = event.bucket.toIntOrNull() ?: return false
    return buckets.any { it.contains(bucket) }
  }
}