// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.filters

import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.internal.statistic.service.fus.StatisticsWhitelistConditions

class LogEventWhitelistFilter(val whitelist: StatisticsWhitelistConditions) : LogEventFilter {
  override fun accepts(event: LogEvent): Boolean {
    return whitelist.accepts(event.group.id, event.group.version, event.build)
  }
}