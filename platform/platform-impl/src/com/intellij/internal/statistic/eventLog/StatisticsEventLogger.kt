// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions
import java.io.File

private val LOG = Logger.getInstance("#com.intellij.internal.statistic.eventLog.StatisticsEventLogger")
private val EP_NAME = ExtensionPointName.create<StatisticsEventLoggerProvider>("com.intellij.statistic.eventLog.eventLoggerProvider")

interface StatisticsEventLogger {
  fun log(group: EventLogGroup, eventId: String, isState: Boolean)
  fun log(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean)
  fun getLogFiles(): List<File>
  fun cleanup()
}

interface StatisticsEventLoggerProvider {
  fun isEnabled() : Boolean
  fun createLogger() : StatisticsEventLogger
}

class EmptyStatisticsEventLoggerProvider : StatisticsEventLoggerProvider {
  override fun isEnabled() : Boolean = false
  override fun createLogger() : StatisticsEventLogger = EmptyStatisticsEventLogger()
}

class EmptyStatisticsEventLogger : StatisticsEventLogger {
  override fun log(group: EventLogGroup, eventId: String, isState: Boolean) = Unit
  override fun log(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean) = Unit
  override fun getLogFiles(): List<File> = emptyList()
  override fun cleanup() = Unit
}

fun getLoggerProvider(): StatisticsEventLoggerProvider {
  if (Extensions.getRootArea().hasExtensionPoint(EP_NAME.name)) {
    val extensions = EP_NAME.extensionList
    if (extensions.isEmpty()) {
      LOG.warn("Cannot find event logger")
      return EmptyStatisticsEventLoggerProvider()
    }
    else if (extensions.size > 1) {
      LOG.warn("Too many loggers registered (${extensions})")
    }
    return extensions[0]
  }
  return EmptyStatisticsEventLoggerProvider()
}

/**
 * Best practices:
 * - Prefer a bigger group with many (related) event types to many small groups of 1-2 events each.
 * - Prefer shorter group names; avoid common prefixes (such as "statistics.").
 */
class EventLogGroup(val id: String, val version: Int)