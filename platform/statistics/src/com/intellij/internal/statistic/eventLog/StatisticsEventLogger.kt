// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private val LOG = Logger.getInstance("#com.intellij.internal.statistic.eventLog.StatisticsEventLogger")
private val EP_NAME = ExtensionPointName<StatisticsEventLoggerProvider>("com.intellij.statistic.eventLog.eventLoggerProvider")

interface StatisticsEventLogger {
  @Deprecated("Use StatisticsEventLogger.logAsync()", ReplaceWith("logAsync(group, eventId, isState)"))
  fun log(group: EventLogGroup, eventId: String, isState: Boolean) {
    logAsync(group, eventId, isState)
  }

  @Deprecated("Use StatisticsEventLogger.logAsync", ReplaceWith("logAsync(group, eventId, data, isState)"))
  fun log(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean) {
    logAsync(group, eventId, data, isState)
  }

  fun logAsync(group: EventLogGroup, eventId: String, isState: Boolean): CompletableFuture<Void> =
    logAsync(group, eventId, Collections.emptyMap(), isState)

  fun logAsync(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean): CompletableFuture<Void>
  fun getActiveLogFile(): EventLogFile?
  fun getLogFilesProvider(): EventLogFilesProvider
  fun cleanup()
  fun rollOver()
}

abstract class StatisticsEventLoggerProvider(val recorderId: String,
                                             val version: Int,
                                             val sendFrequencyMs: Long = TimeUnit.HOURS.toMillis(1),
                                             private val maxFileSize: String = "200KB") {
  open val logger: StatisticsEventLogger by lazy { createLogger() }

  abstract fun isRecordEnabled() : Boolean
  abstract fun isSendEnabled() : Boolean

  fun getActiveLogFile(): EventLogFile? {
    return logger.getActiveLogFile()
  }

  fun getLogFilesProvider(): EventLogFilesProvider {
    return logger.getLogFilesProvider()
  }

  private fun createLogger(): StatisticsEventLogger {
    if (!isRecordEnabled()) {
      return EmptyStatisticsEventLogger()
    }

    val app = ApplicationManager.getApplication()
    val isEap = app != null && app.isEAP
    val config = EventLogConfiguration
    val writer = EventLogNotificationProxy(StatisticsEventLogFileWriter(recorderId, maxFileSize, isEap, config.build), recorderId)
    val logger = StatisticsFileEventLogger(recorderId, config.sessionId, config.build, config.bucket.toString(), version.toString(), writer,
                                           UsageStatisticsPersistenceComponent.getInstance())
    Disposer.register(ApplicationManager.getApplication(), logger)
    return logger
  }
}

internal class EmptyStatisticsEventLoggerProvider(recorderId: String): StatisticsEventLoggerProvider(recorderId, 0, -1) {
  override val logger: StatisticsEventLogger = EmptyStatisticsEventLogger()

  override fun isRecordEnabled() = false
  override fun isSendEnabled() = false
}

internal class EmptyStatisticsEventLogger : StatisticsEventLogger {
  override fun getActiveLogFile(): EventLogFile? = null
  override fun getLogFilesProvider(): EventLogFilesProvider = EmptyEventLogFilesProvider
  override fun cleanup() = Unit
  override fun rollOver() = Unit
  override fun logAsync(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean): CompletableFuture<Void> =
    CompletableFuture.completedFuture(null)
}

object EmptyEventLogFilesProvider: EventLogFilesProvider {
  override fun getLogFilesDir(): Path? = null

  override fun getLogFiles(): List<EventLogFile> = emptyList()
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

@Deprecated("Use EventLogGroup instead")
class FeatureUsageGroup(val id: String, val version: Int)