// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.StatisticsServiceScope
import com.intellij.internal.statistic.eventLog.events.EventFieldIds
import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.File
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

interface StatisticsEventLogger {
  fun logAsync(group: EventLogGroup, eventId: String, isState: Boolean): CompletableFuture<*> {
    return logAsync(group = group, eventId = eventId, data = Collections.emptyMap(), isState = isState)
  }

  fun logAsync(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean): CompletableFuture<*>

  fun logAsync(group: EventLogGroup, eventId: String, dataProvider: () -> Map<String, Any>?, isState: Boolean): CompletableFuture<*>

  fun computeAsync(computation: (backgroundThreadExecutor: Executor) -> Unit)

  fun getActiveLogFile(): EventLogFile?

  fun getLogFilesProvider(): EventLogFilesProvider

  fun cleanup()

  fun rollOver()
}

/**
 * Represents the recorder.
 *
 * [useDefaultRecorderId] - When enabled, device and machine ids would match FUS(default) recorder. Must NOT be enabled for non-anonymized recorders.
 */
abstract class StatisticsEventLoggerProvider(
  val recorderId: String,
  val version: Int,
  val sendFrequencyMs: Long,
  @get:Internal val maxFileSizeInBytes: Int,
  val sendLogsOnIdeClose: Boolean = false,
  val isCharsEscapingRequired: Boolean = true,
  val useDefaultRecorderId: Boolean = false,
) {
  open val coroutineScope: CoroutineScope = StatisticsServiceScope.getScope()

  @Internal
  val recorderOptionsProvider: RecorderOptionProvider

  init {
    // add existing options
    LOG.info("Initialize event logger provider for '$recorderId'")
    val configOptionsService = EventLogConfigOptionsService.getInstance()
    recorderOptionsProvider = RecorderOptionProvider(configOptionsService.getOptions(recorderId).allOptions)

    // options can also be changed during the lifetime of the application
    ApplicationManager.getApplication().messageBus.connect(coroutineScope)
      .subscribe(EventLogConfigOptionsService.TOPIC, EventLogConfigOptionsListener { changedRecorder, options ->
        if (changedRecorder == recorderId) {
          recorderOptionsProvider.update(options)
        }
      })
  }

  @Deprecated(message = "Use primary constructor instead")
  constructor(
    recorderId: String,
    version: Int,
    sendFrequencyMs: Long,
    maxFileSizeInBytes: Int,
  ) : this(
    recorderId = recorderId,
    version = version,
    sendFrequencyMs = sendFrequencyMs,
    maxFileSizeInBytes = maxFileSizeInBytes,
    sendLogsOnIdeClose = false,
  )

  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<StatisticsEventLoggerProvider> =
      ExtensionPointName("com.intellij.statistic.eventLog.eventLoggerProvider")
    const val DEFAULT_MAX_FILE_SIZE_BYTES: Int = 200 * 1024
    val DEFAULT_SEND_FREQUENCY_MS: Long = TimeUnit.HOURS.toMillis(1)

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
      catch (_: NumberFormatException) {
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


  private val localLogger: StatisticsEventLogger by lazy { createLocalLogger() }
  private val actualLogger: StatisticsEventLogger by lazy { createLogger() }
  internal val eventLogSystemLogger: EventLogSystemCollector by lazy { EventLogSystemCollector(this) }

  open val logger: StatisticsEventLogger
    get() = if (isLoggingEnabled()) actualLogger else localLogger

  abstract fun isRecordEnabled(): Boolean

  abstract fun isSendEnabled(): Boolean

  /**
   * Determines if logging code should be executed on logging method calls
   * */
  fun isLoggingEnabled(): Boolean = isRecordEnabled() || isLoggingAlwaysActive()

  /**
   * Determines if logging of events should happen in code even if recording of events to file is disabled
   * */
  open fun isLoggingAlwaysActive(): Boolean = false

  fun getActiveLogFile(): EventLogFile? = logger.getActiveLogFile()

  fun getLogFilesProvider(): EventLogFilesProvider = logger.getLogFilesProvider()

  /**
   * Merge strategy defines which successive events should be merged and recorded as a single event.
   * The number of merged events is reflected in `com.intellij.internal.statistic.eventLog.LogEventAction#count` field.
   *
   * By default, only events with the same values in group id, event id and all event data fields are merged.
   */
  @Internal
  open fun createEventsMergeStrategy(): StatisticsEventMergeStrategy {
    return FilteredEventMergeStrategy(mergeIgnoredFields)
  }

  /**
   * Event data fields excluded from merge equality (e.g. `start_time`), so successive events that differ only in
   * these fields still merge into a single counted event.
   */
  @get:Internal
  open val mergeIgnoredFields: Set<String>
    get() = EventFieldIds.FieldsIgnoredByMerge.toSet()

  private fun createLogger(): StatisticsEventLogger {
    val eventLogConfiguration = EventLogConfiguration.getInstance()
    val config = eventLogConfiguration.getOrCreate(
      recorderId = recorderId,
      alternativeRecorderId = if (useDefaultRecorderId) "FUS" else null,
    )

    val fusClient = IntellijSensitiveDataValidator.getInstance(recorderId).fusClient
                    ?: error("FusComponents.fusClient is null for recorder '$recorderId'; logger creation requires the production FusComponents path.")
    val eventLogDir = eventLogConfiguration.getEventLogDataPath().resolve("logs").resolve(recorderId)

    val logger = StatisticsFileEventLogger(
      recorderId = recorderId,
      sessionId = config.sessionId,
      build = eventLogConfiguration.build,
      bucket = config.bucket.toString(),
      recorderVersion = version.toString(),
      // Events flow to the FusClient; the SDK dispatcher's preEventWrite injects the system fields
      // (system_event_id, system_headless, ide_mode, product_mode, auto_license_type). See FusComponentProvider.
      eventWriter = fusClient,
      eventLogDir = eventLogDir
    )

    coroutineScope.coroutineContext.job.invokeOnCompletion { Disposer.dispose(logger) }
    return logger
  }

  private fun createLocalLogger(): StatisticsEventLogger {
    val eventLogConfiguration = EventLogConfiguration.getInstance()

    val logger = LocalStatisticsFileEventLogger(
      recorderId = recorderId,
      build = eventLogConfiguration.build,
      recorderVersion = version.toString(),
      mergeStrategy = createEventsMergeStrategy(),
      coroutineScope = coroutineScope,
    )
    Disposer.register(ApplicationManager.getApplication(), logger)
    return logger
  }
}

/**
 * For internal use only.
 *
 * Holds default implementation of StatisticsEventLoggerProvider.isLoggingAlwaysActive
 * to connect logger with [ExternalEventLogSettings] and
 * [ExternalEventLogListenerProviderExtension]
 * */
abstract class StatisticsEventLoggerProviderExt(
  recorderId: String,
  version: Int,
  sendFrequencyMs: Long,
  maxFileSizeInBytes: Int,
  sendLogsOnIdeClose: Boolean = false,
) :
  StatisticsEventLoggerProvider(
    recorderId = recorderId,
    version = version,
    sendFrequencyMs = sendFrequencyMs,
    maxFileSizeInBytes = maxFileSizeInBytes,
    sendLogsOnIdeClose = sendLogsOnIdeClose,
    isCharsEscapingRequired = false,
  ) {
  override fun isLoggingAlwaysActive(): Boolean = StatisticsEventLogProviderUtil.forceLoggingAlwaysEnabled()
}

internal class EmptyStatisticsEventLoggerProvider(recorderId: String) : StatisticsEventLoggerProvider(
  recorderId = recorderId,
  version = 1,
  sendFrequencyMs = -1,
  maxFileSizeInBytes = DEFAULT_MAX_FILE_SIZE_BYTES,
) {
  override val logger: StatisticsEventLogger = EmptyStatisticsEventLogger()

  override fun isRecordEnabled() = false

  override fun isSendEnabled() = false
}

internal class EmptyStatisticsEventLogger : StatisticsEventLogger {
  override fun getActiveLogFile(): EventLogFile? = null

  override fun getLogFilesProvider(): EventLogFilesProvider = EmptyEventLogFilesProvider

  override fun cleanup() {
  }

  override fun rollOver() {
  }

  override fun logAsync(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean): CompletableFuture<Void> {
    return CompletableFuture.completedFuture(null)
  }

  override fun logAsync(
    group: EventLogGroup,
    eventId: String,
    dataProvider: () -> Map<String, Any>?,
    isState: Boolean,
  ): CompletableFuture<Void> = CompletableFuture.completedFuture(null)

  override fun computeAsync(computation: (backgroundThreadExecutor: Executor) -> Unit) {
  }
}

object EmptyEventLogFilesProvider : EventLogFilesProvider {
  override fun getLogFiles(): List<File> = emptyList()

  override fun getLogFilesExceptActive(): List<File> = emptyList()
}
