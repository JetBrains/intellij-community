// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.filters

import com.intellij.internal.statistic.eventLog.EventLogBuild
import com.jetbrains.fus.reporting.model.lion3.LogEvent

object LogEventSnapshotBuildFilter : LogEventFilter {
  override fun accepts(event: LogEvent): Boolean {
    val parts = EventLogBuild.fromString(event.build)?.components
    return parts != null && (parts.size != 2 || parts[1] != 0)
  }
}