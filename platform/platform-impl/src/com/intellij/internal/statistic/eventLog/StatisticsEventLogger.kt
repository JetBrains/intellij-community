// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import java.util.concurrent.TimeUnit

private val LOG = Logger.getInstance("#com.intellij.internal.statistic.eventLog.StatisticsEventLogger")
private val EP_NAME = ExtensionPointName.create<StatisticsEventLoggerProvider>("com.intellij.statistic.eventLog.eventLoggerProvider")

interface StatisticsEventLogger {
  fun log(group: EventLogGroup, eventId: String, isState: Boolean)
  fun log(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean)
  fun getActiveLogFile(): EventLogFile?
  fun getLogFiles(): List<EventLogFile>
  fun cleanup()
  fun rollOver()
}

abstract class StatisticsEventLoggerProvider(val recorderId: String,
                                             val version: Int,
                                             val sendFrequencyMs: Long = TimeUnit.HOURS.toMillis(1),
                                             private val maxFileSize: String = "200KB") {
  open val logger: StatisticsEventLogger = createLogger()

  abstract fun isRecordEnabled() : Boolean
  abstract fun isSendEnabled() : Boolean

  fun getActiveLogFile(): EventLogFile? {
    return logger.getActiveLogFile()
  }

  fun getLogFiles(): List<EventLogFile> {
    return logger.getLogFiles()
  }

  private fun createLogger(): StatisticsEventLogger {
    if (!isRecordEnabled()) {
      return EmptyStatisticsEventLogger()
    }

    val app = ApplicationManager.getApplication()
    val isEap = app != null && app.isEAP
    val config = EventLogConfiguration
    val writer = EventLogNotificationProxy(StatisticsEventLogFileWriter(recorderId, maxFileSize, isEap, config.build), recorderId)
    val logger = StatisticsFileEventLogger(recorderId, config.sessionId, config.build, config.bucket.toString(), version.toString(), writer)
    Disposer.register(ApplicationManager.getApplication(), logger)
    return logger
  }
}

class EmptyStatisticsEventLoggerProvider(recorderId: String): StatisticsEventLoggerProvider(recorderId, 0, -1) {
  override val logger: StatisticsEventLogger = EmptyStatisticsEventLogger()

  override fun isRecordEnabled(): Boolean {
    return false
  }
  override fun isSendEnabled(): Boolean {
    return false
  }
}

class EmptyStatisticsEventLogger : StatisticsEventLogger {
  override fun log(group: EventLogGroup, eventId: String, isState: Boolean) = Unit
  override fun log(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean) = Unit
  override fun getActiveLogFile(): EventLogFile? = null
  override fun getLogFiles(): List<EventLogFile> = emptyList()
  override fun cleanup() = Unit
  override fun rollOver() = Unit
}

fun getEventLogProviders(): List<StatisticsEventLoggerProvider> {
  return EP_NAME.extensionsIfPointIsRegistered
}

fun getEventLogProvider(recorderId: String): StatisticsEventLoggerProvider {
  if (ApplicationManager.getApplication().extensionArea.hasExtensionPoint(EP_NAME.name)) {
    EP_NAME.findFirstSafe { it.recorderId == recorderId }?.let { return it }
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

@Deprecated("Use EventLogGroup instead")
class FeatureUsageGroup(val id: String, val version: Int)