// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.filters

import com.intellij.internal.statistic.eventLog.LogEvent

interface LogEventFilter {
  fun accepts(event: LogEvent) : Boolean
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