// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import java.io.File

private val LOG = Logger.getInstance("#com.intellij.internal.statistic.eventLog.StatisticsEventLogger")
private val EP_NAME = ExtensionPointName.create<StatisticsEventLoggerProvider>("com.intellij.statistic.eventLog.eventLoggerProvider")

interface StatisticsEventLogger {
  fun log(group: EventLogGroup, eventId: String, isState: Boolean)
  fun log(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean)
  fun getLogFiles(): List<File>
  fun cleanup()
}

abstract class StatisticsEventLoggerProvider(val recorderId: String, val version: Int, val sendFrequencyMs: Long) {
  abstract fun isRecordEnabled() : Boolean
  abstract fun isSendEnabled() : Boolean

  open fun createLogger(): StatisticsEventLogger {
    if (!isRecordEnabled()) {
      return EmptyStatisticsEventLogger()
    }

    val config = EventLogConfiguration
    val bucket = config.bucket.toString()
    val logger = StatisticsFileEventLogger(config.sessionId, config.build, bucket, version.toString(), StatisticsEventLogFileWriter())
    Disposer.register(ApplicationManager.getApplication(), logger)
    return logger
  }
}

class EmptyStatisticsEventLoggerProvider(recorderId: String): StatisticsEventLoggerProvider(recorderId, 0, -1) {
  override fun isRecordEnabled(): Boolean {
    return false
  }
  override fun isSendEnabled(): Boolean {
    return false
  }

  override fun createLogger(): StatisticsEventLogger {
    return EmptyStatisticsEventLogger()
  }

}
class EmptyStatisticsEventLogger : StatisticsEventLogger {
  override fun log(group: EventLogGroup, eventId: String, isState: Boolean) = Unit
  override fun log(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean) = Unit
  override fun getLogFiles(): List<File> = emptyList()
  override fun cleanup() = Unit
}

fun getEventLogProviders(): List<StatisticsEventLoggerProvider> {
  if (Extensions.getRootArea().hasExtensionPoint(EP_NAME.name)) {
    return EP_NAME.extensionList
  }
  return emptyList()
}


fun getEventLogProvider(recorderId: String): StatisticsEventLoggerProvider {
  val providers = getEventLogProviders()
  for (provider in providers) {
    if (StringUtil.equals(provider.recorderId, recorderId)) {
      return provider
    }
  }
  LOG.warn("Cannot find event log provider with recorder-id=${recorderId}")
  return EmptyStatisticsEventLoggerProvider(recorderId)
}

/**
 * Best practices:
 * - Prefer a bigger group with many (related) event types to many small groups of 1-2 events each.
 * - Prefer shorter group names; avoid common prefixes (such as "statistics.").
 */
class EventLogGroup(val id: String, val version: Int)