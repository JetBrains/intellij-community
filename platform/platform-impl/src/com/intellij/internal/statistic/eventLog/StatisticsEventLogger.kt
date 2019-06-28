// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import java.io.File
import java.util.concurrent.TimeUnit

private val LOG = Logger.getInstance("#com.intellij.internal.statistic.eventLog.StatisticsEventLogger")
private val EP_NAME = ExtensionPointName.create<StatisticsEventLoggerProvider>("com.intellij.statistic.eventLog.eventLoggerProvider")

interface StatisticsEventLogger {
  fun log(group: EventLogGroup, eventId: String, isState: Boolean)
  fun log(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean)
  fun getActiveLogFile(): File?
  fun getLogFiles(): List<File>
  fun cleanup()
  fun rollOver()
}

abstract class StatisticsEventLoggerProvider(val recorderId: String,
                                             val version: Int,
                                             val sendFrequencyMs: Long = TimeUnit.HOURS.toMillis(1),
                                             val maxFileSize: String = "200KB") {
  open val logger: StatisticsEventLogger = createLogger()

  abstract fun isRecordEnabled() : Boolean
  abstract fun isSendEnabled() : Boolean

  fun getActiveLogFile(): File? {
    return logger.getActiveLogFile()
  }

  fun getLogFiles(): List<File> {
    return logger.getLogFiles()
  }

  private fun createLogger(): StatisticsEventLogger {
    if (!isRecordEnabled()) {
      return EmptyStatisticsEventLogger()
    }

    val config = EventLogConfiguration
    val writer = StatisticsEventLogFileWriter(recorderId, maxFileSize)
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
  override fun getActiveLogFile(): File? = null
  override fun getLogFiles(): List<File> = emptyList()
  override fun cleanup() = Unit
  override fun rollOver() = Unit
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

@Deprecated("Use EventLogGroup instead")
class FeatureUsageGroup(val id: String, val version: Int)