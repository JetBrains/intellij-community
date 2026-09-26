// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.concurrency.ExecutionInitiator
import com.intellij.internal.statistic.eventLog.validator.IntellijSensitiveDataValidator
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.AppExecutorUtil
import com.jetbrains.fus.reporting.FeatureUsageLogWriter
import com.jetbrains.fus.reporting.FusClient
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEventAction
import com.jetbrains.fus.reporting.model.lion3.LogEventGroup
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

@ApiStatus.Internal
val LICENSE_CODE_KEY: Key<Char> = Key("LICENSE_CODE")

@ApiStatus.Internal
const val FREE_LICENSE_CODE: Char = 'N'

@ApiStatus.Internal
fun isFreeLicenseCodeSet(): Boolean = ApplicationManager.getApplication().getUserData(LICENSE_CODE_KEY) == FREE_LICENSE_CODE

@ApiStatus.Internal
open class StatisticsFileEventLogger(
  private val recorderId: String,
  private val sessionId: String,
  private val build: String,
  private val bucket: String,
  private val recorderVersion: String,
  private val eventWriter: FeatureUsageLogWriter<LogEvent>,
  private val eventLogDir: Path
) : StatisticsEventLogger, Disposable {
  /**
   * On the IntelliJ platform we currently must run event logging on a single thread because the `preEventWrite` hook
   * in [com.intellij.internal.statistic.eventLog.validator.storage.FusComponentProvider] depends on the order of events.
   * If we can do without the event timestamps of previous events, we could technically run event logging on a coroutine dispatcher.
   */
  protected val logExecutor: ExecutorService = AppExecutorUtil.createBoundedApplicationPoolExecutor("StatisticsFileEventLogger", 1)
  private val escapeCharsInData: Boolean = StatisticsRecorderUtil.isCharsEscapingRequired(recorderId)

  @Suppress("RAW_RUN_BLOCKING")
  override fun logAsync(
    group: EventLogGroup,
    eventId: String,
    dataProvider: () -> Map<String, Any>?,
    isState: Boolean,
  ): CompletableFuture<Void> {
    val eventTime = System.currentTimeMillis()
    group.validateEventId(eventId)
    val initiator = ExecutionInitiator.currentOrNull()?.takeIf { !isState }
    return try {
      CompletableFuture.runAsync(Runnable {
        val validator = IntellijSensitiveDataValidator.getInstance(recorderId)
        if (runBlocking { !validator.isGroupAllowed(group) }) {
          return@Runnable
        }
        val data = dataProvider() ?: return@Runnable
        val eventData = HashMap(data).also { it.remove(FeatureUsageData.JCP_DATA_KEY) }
          .also { data -> initiator?.let { data["initiated_by"] = it.id } }
        val event = LogEvent(
          session = sessionId,
          build = build,
          bucket = bucket,
          time = eventTime,
          group = LogEventGroup(group.id, group.version.toString()),
          recorderVersion = recorderVersion,
          event = LogEventAction(eventId, isState, eventData),
        )
          .also { if (escapeCharsInData) it.escape() else it.escapeExceptData() }
        // Validation runs once, inside the dispatcher (IntellijReportValidator), after merge and
        // system-field injection. The isGroupAllowed check above stays as a cheap early-out.
        eventWriter.queueEvent(event)
      }, logExecutor)
    }
    catch (e: RejectedExecutionException) {
      //executor is shutdown
      CompletableFuture<Void>().also { it.completeExceptionally(e) }
    }
  }

  override fun computeAsync(computation: (backgroundThreadExecutor: Executor) -> Unit) {
    computation(logExecutor)
  }

  override fun logAsync(
    group: EventLogGroup,
    eventId: String,
    data: Map<String, Any>,
    isState: Boolean,
  ): CompletableFuture<Void> {
    return logAsync(group, eventId, { data }, isState)
  }

  override fun getActiveLogFile(): EventLogFile? {
    val active = activeLogFileName() ?: return null
    return EventLogFile(eventLogDir.resolve(active).toFile())
  }

  override fun getLogFilesProvider(): EventLogFilesProvider = DefaultEventLogFilesProvider(eventLogDir) { activeLogFileName() }

  override fun cleanup() {
    // Best-effort: remove the recorder's queue files. PersistentQueue recreates them on the next write.
    eventLogDir.toFile().listFiles()?.filter { it.name.endsWith(".log") || it.name.endsWith(".log.meta") }?.forEach { it.delete() }
  }

  override fun rollOver() {
    // PersistentQueue rotates on size internally; there is no forced-rollover API today, so this is a no-op.
  }

  // PersistentQueue appends to the most recently modified `.log` file.
  private fun activeLogFileName(): String? =
    eventLogDir.toFile().listFiles()?.filter { it.name.endsWith(".log") }?.maxByOrNull { it.lastModified() }?.name

  override fun dispose() {
    try {
      // `logExecutor` is FIFO, so this task runs after every event that the shutdown logged, `ide.close` included.
      CompletableFuture.runAsync({ closeEventWriter() }, logExecutor).get(1, TimeUnit.SECONDS)
    }
    catch (_: Exception) {
      // executor may already be shut down, interrupted, or timed out; last event is lost in that case
    }
    logExecutor.shutdown()
  }

  fun flush(): CompletableFuture<Void> {
    return try {
      CompletableFuture.runAsync({ flushEventWriter() }, logExecutor)
    }
    catch (e: RejectedExecutionException) {
      // the executor is shut down, which happens when a scheduled flush lands after dispose
      CompletableFuture<Void>().also { it.completeExceptionally(e) }
    }
  }

  private fun flushEventWriter() {
    when (val writer = eventWriter) {
      // The production path. It skips the flush when no event was written, so dispose does not build the client.
      is LazyFusClientLogWriter -> writer.flushEventsIfInitialized()
      // A test can pass a FusClient directly.
      is FusClient<LogEvent, *> -> writer.flushEvents()
      else -> {}
    }
  }

  private fun closeEventWriter() {
    when (val writer = eventWriter) {
      is LazyFusClientLogWriter -> writer.closeIfInitialized()
      is FusClient<LogEvent, *> -> writer.close()
      else -> {}
    }
  }
}
