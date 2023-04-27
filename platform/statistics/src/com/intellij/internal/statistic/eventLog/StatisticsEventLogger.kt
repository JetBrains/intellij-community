// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.idea.AppMode
import com.intellij.internal.statistic.eventLog.logger.StatisticsEventLogThrottleWriter
import com.intellij.internal.statistic.persistence.UsageStatisticsPersistenceComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

interface StatisticsEventLogger {
  @Deprecated("Use StatisticsEventLogger.logAsync()", ReplaceWith("logAsync(group, eventId, isState)"))
  @ApiStatus.ScheduledForRemoval
  fun log(group: EventLogGroup, eventId: String, isState: Boolean) {
    logAsync(group, eventId, isState)
  }

  @Deprecated("Use StatisticsEventLogger.logAsync", ReplaceWith("logAsync(group, eventId, data, isState)"))
  @ApiStatus.ScheduledForRemoval
  fun log(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean) {
    logAsync(group, eventId, data, isState)
  }

  fun logAsync(group: EventLogGroup, eventId: String, isState: Boolean): CompletableFuture<Void> =
    logAsync(group, eventId, Collections.emptyMap(), isState)

  fun logAsync(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean): CompletableFuture<Void>
  fun logAsync(group: EventLogGroup, eventId: String, dataProvider: () -> Map<String, Any>?, isState: Boolean): CompletableFuture<Void>
  fun computeAsync(computation: (backgroundThreadExecutor: Executor) -> Unit)
  fun getActiveLogFile(): EventLogFile?
  fun getLogFilesProvider(): EventLogFilesProvider
  fun cleanup()
  fun rollOver()
}

abstract class StatisticsEventLoggerProvider(val recorderId: String,
                                             val version: Int,
                                             val sendFrequencyMs: Long,
                                             private val maxFileSizeInBytes: Int,
                                             val sendLogsOnIdeClose: Boolean = false) {

  @Deprecated(message = "Use primary constructor instead")
  constructor(recorderId: String,
              version: Int,
              sendFrequencyMs: Long = TimeUnit.HOURS.toMillis(1),
              maxFileSize: String = "200KB") : this(recorderId, version, sendFrequencyMs, parseFileSize(maxFileSize))

  @Deprecated(message = "Use primary constructor instead")
  constructor(recorderId: String,
              version: Int,
              sendFrequencyMs: Long,
              maxFileSizeInBytes: Int) : this(recorderId, version, sendFrequencyMs, maxFileSizeInBytes, false)

  companion object {
    @JvmStatic
    val EP_NAME = ExtensionPointName<StatisticsEventLoggerProvider>("com.intellij.statistic.eventLog.eventLoggerProvider")
    const val DEFAULT_MAX_FILE_SIZE_BYTES = 200 * 1024
    val DEFAULT_SEND_FREQUENCY_MS = TimeUnit.HOURS.toMillis(1)

    private val LOG = logger<StatisticsEventLoggerProvider>()

    fun parseFileSize(maxFileSize: String): Int {
      val length = maxFileSize.length
      if (length < 3) {
        LOG.warn("maxFileSize should contain measurement unit: $maxFileSize")
        return DEFAULT_MAX_FILE_SIZE_BYTES
      }
      val value = maxFileSize.substring(0, length - 2)
      val size = try {
        value.toInt()
      }
      catch (e: NumberFormatException) {
        LOG.warn("Unable to parse maxFileSize for FUS log file: $maxFileSize")
        return DEFAULT_MAX_FILE_SIZE_BYTES
      }
      val multiplier = when (maxFileSize.substring(length - 2, length)) {
        "KB" -> 1024
        "MB" -> 1024 * 1024
        "GB" -> 1024 * 1024 * 1024
        else -> {
          LOG.warn("Unable to parse measurement unit of maxFileSize for FUS log file: $maxFileSize")
          return DEFAULT_MAX_FILE_SIZE_BYTES
        }
      }
      return size * multiplier
    }
  }

  private val emptyLogger: StatisticsEventLogger by lazy { EmptyStatisticsEventLogger() }
  private val actualLogger: StatisticsEventLogger by lazy { createLogger() }

  open val logger: StatisticsEventLogger
    get() = if (isLoggingEnabled()) actualLogger else emptyLogger

  abstract fun isRecordEnabled() : Boolean
  abstract fun isSendEnabled() : Boolean
  /**
   * Determines if logging code should be executed on logging method calls
   * */
  final fun isLoggingEnabled(): Boolean = isRecordEnabled() || isLoggingAlwaysActive()
  /**
   * Determines if logging of events should happen in code even if recording of events to file is disabled
  * */
  open fun isLoggingAlwaysActive(): Boolean = false

  fun getActiveLogFile(): EventLogFile? {
    return logger.getActiveLogFile()
  }

  fun getLogFilesProvider(): EventLogFilesProvider {
    return logger.getLogFilesProvider()
  }

  /**
   * Merge strategy defines which successive events should be merged and recorded as a single event.
   * The amount of merged events is reflected in `com.intellij.internal.statistic.eventLog.LogEventAction#count` field.
   *
   * By default, only events with the same values in group id, event id and all event data fields are merged.
   */
  open fun createEventsMergeStrategy(): StatisticsEventMergeStrategy {
    return FilteredEventMergeStrategy(emptySet())
  }

  private fun createLogger(): StatisticsEventLogger {
    val app = ApplicationManager.getApplication()
    val isEap = app != null && app.isEAP
    val isHeadless = app != null && app.isHeadlessEnvironment
    // Use `String?` instead of boolean flag for future expansion with other IDE modes
    val ideMode = if(AppMode.isRemoteDevHost()) "RDH" else null
    val eventLogConfiguration = EventLogConfiguration.getInstance()
    val config = eventLogConfiguration.getOrCreate(recorderId)
    val writer = StatisticsEventLogFileWriter(recorderId, this, maxFileSizeInBytes, isEap, eventLogConfiguration.build)

    val configService = EventLogConfigOptionsService.getInstance()
    val throttledWriter = StatisticsEventLogThrottleWriter(
      configService, recorderId, version.toString(), writer
    )

    val logger = StatisticsFileEventLogger(
      recorderId, config.sessionId, isHeadless, eventLogConfiguration.build, config.bucket.toString(), version.toString(),
      throttledWriter, UsageStatisticsPersistenceComponent.getInstance(), createEventsMergeStrategy(), ideMode
    )
    Disposer.register(ApplicationManager.getApplication(), logger)
    return logger
  }
}

/**
 * For internal use only.
 *
 * Holds default implementation of StatisticsEventLoggerProvider.isLoggingAlwaysActive
 * to connect logger with com.intellij.internal.statistic.eventLog.ExternalEventLogSettings
 * */
abstract class StatisticsEventLoggerProviderExt(recorderId: String, version: Int, sendFrequencyMs: Long,
                                                maxFileSizeInBytes: Int, sendLogsOnIdeClose: Boolean = false) :
  StatisticsEventLoggerProvider(recorderId, version, sendFrequencyMs, maxFileSizeInBytes, sendLogsOnIdeClose) {
  override fun isLoggingAlwaysActive(): Boolean =
    StatisticsEventLogProviderUtil.getExternalEventLogSettings()?.forceLoggingAlwaysEnabled() ?: false
}

internal class EmptyStatisticsEventLoggerProvider(recorderId: String): StatisticsEventLoggerProvider(recorderId, 0, -1, DEFAULT_MAX_FILE_SIZE_BYTES) {
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
  override fun logAsync(group: EventLogGroup, eventId: String, dataProvider: () -> Map<String, Any>?, isState: Boolean): CompletableFuture<Void> =
    CompletableFuture.completedFuture(null)
  override fun computeAsync(computation: (backgroundThreadExecutor: Executor) -> Unit) {}
}

object EmptyEventLogFilesProvider: EventLogFilesProvider {
  override fun getLogFiles(): List<File> = emptyList()

  override fun getLogFilesExceptActive(): List<File> = emptyList()
}

@ApiStatus.ScheduledForRemoval
@Deprecated("Use StatisticsEventLogProviderUtil.getEventLogProvider(String)",
            ReplaceWith("StatisticsEventLogProviderUtil.getEventLogProvider(recorderId)"))
fun getEventLogProvider(recorderId: String): StatisticsEventLoggerProvider {
  return StatisticsEventLogProviderUtil.getEventLogProvider(recorderId)
}
