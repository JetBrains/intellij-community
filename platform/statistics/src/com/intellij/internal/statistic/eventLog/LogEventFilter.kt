// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.service.fus.FUSWhitelist
import com.intellij.openapi.util.BuildNumber

interface LogEventFilter {
  fun accepts(event: LogEvent) : Boolean
}

class LogEventWhitelistFilter(val whitelist: FUSWhitelist) : LogEventFilter {
  override fun accepts(event: LogEvent): Boolean {
    return whitelist.accepts(event.group.id, event.group.version, event.build)
  }
}

object LogEventSnapshotBuildFilter : LogEventFilter {
  override fun accepts(event: LogEvent): Boolean {
    val parts = BuildNumber.fromString(event.build).components
    return parts.size != 2 || parts[1] != 0
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