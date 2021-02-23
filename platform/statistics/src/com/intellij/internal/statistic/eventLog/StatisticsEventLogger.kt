// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.eventLog.logger.StatisticsEventLogThrottleWriter
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

interface StatisticsEventLogger {
  @Deprecated("Use StatisticsEventLogger.logAsync()", ReplaceWith("logAsync(group, eventId, isState)"))
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  fun log(group: EventLogGroup, eventId: String, isState: Boolean) {
    logAsync(group, eventId, isState)
  }

  @Deprecated("Use StatisticsEventLogger.logAsync", ReplaceWith("logAsync(group, eventId, data, isState)"))
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
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
    val isHeadless = app != null && app.isHeadlessEnvironment
    val config = EventLogConfiguration.getOrCreate(recorderId)
    val writer = StatisticsEventLogFileWriter(recorderId, maxFileSize, isEap, EventLogConfiguration.build)

    val configService = EventLogConfigOptionsService.getInstance()
    val throttledWriter = StatisticsEventLogThrottleWriter(
      configService, recorderId, version.toString(), EventLogNotificationProxy(writer, recorderId)
    )

    val logger = StatisticsFileEventLogger(
      recorderId, config.sessionId, isHeadless, EventLogConfiguration.build, config.bucket.toString(), version.toString(), throttledWriter,
      UsageStatisticsPersistenceComponent.getInstance()
    )
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

@Deprecated("Use StatisticsEventLogProviderUtil.getEventLogProvider(String)",
            ReplaceWith("StatisticsEventLogProviderUtil.getEventLogProvider(recorderId)"))
fun getEventLogProvider(recorderId: String): StatisticsEventLoggerProvider {
  return StatisticsEventLogProviderUtil.getEventLogProvider(recorderId)
}