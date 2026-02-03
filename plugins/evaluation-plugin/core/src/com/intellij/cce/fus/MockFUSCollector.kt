package com.intellij.cce.fus

import com.intellij.internal.statistic.eventLog.EmptyEventLogFilesProvider
import com.intellij.internal.statistic.eventLog.EventLogFile
import com.intellij.internal.statistic.eventLog.EventLogFilesProvider
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.EventLogListenersManager
import com.intellij.internal.statistic.eventLog.StatisticsEventLogger
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.eventLog.escape
import com.intellij.internal.statistic.eventLog.newLogEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

object MockFUSCollector {
  suspend fun <T> collectLogEvents(
    parentDisposable: Disposable,
    action: suspend () -> T,
  ): Pair<List<LogEvent>, T> {

    val mockLoggerProvider = MockStatisticsEventLoggerProvider("ML")
    val llmcLoggerProvider = MockStatisticsEventLoggerProvider("LLMC")
    (StatisticsEventLoggerProvider.EP_NAME.point as ExtensionPointImpl<StatisticsEventLoggerProvider>)
      .maskAll(listOf(mockLoggerProvider, llmcLoggerProvider), parentDisposable, true)
    val actionResult = action()
    return mockLoggerProvider.getLoggedEvents() to actionResult
  }

  fun isMocked(logger: StatisticsEventLogger): Boolean = logger is MockStatisticsEventLoggerProvider.MockStatisticsEventLogger
}

private class MockStatisticsEventLoggerProvider(recorderId: String) : StatisticsEventLoggerProvider(recorderId,
                                                                                                    1,
                                                                                                    DEFAULT_SEND_FREQUENCY_MS,
                                                                                                    DEFAULT_MAX_FILE_SIZE_BYTES,
                                                                                                    false,
                                                                                                    true) {
  override val logger: MockStatisticsEventLogger = MockStatisticsEventLogger(recorderId)

  override fun isRecordEnabled(): Boolean = true

  override fun isSendEnabled(): Boolean = false

  fun getLoggedEvents(): List<LogEvent> = logger.logged

  class MockStatisticsEventLogger(private val recorderId: String,
                                  private val session: String = "mockSession",
                                  private val build: String = "999.999",
                                  private val bucket: String = "1",
                                  private val recorderVersion: String = "1") : StatisticsEventLogger {
    val logged = CopyOnWriteArrayList<LogEvent>()

    override fun logAsync(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean): CompletableFuture<Void> {
      val eventTime = System.currentTimeMillis()

      val event = newLogEvent(session, build, bucket, eventTime, group.id, group.version.toString(), recorderVersion, eventId, isState,
                              data)
        .escape()
      logged.add(event)
      ApplicationManager.getApplication().getService(EventLogListenersManager::class.java)
        .notifySubscribers(recorderId, event, eventId, data, false)

      return CompletableFuture.completedFuture(null)
    }

    override fun logAsync(group: EventLogGroup,
                          eventId: String,
                          dataProvider: () -> Map<String, Any>?,
                          isState: Boolean): CompletableFuture<Void> {
      val data = dataProvider() ?: return CompletableFuture.completedFuture(null)
      return logAsync(group, eventId, data, isState)
    }

    override fun computeAsync(computation: (backgroundThreadExecutor: Executor) -> Unit) {
    }

    override fun getActiveLogFile(): EventLogFile? = null

    override fun getLogFilesProvider(): EventLogFilesProvider = EmptyEventLogFilesProvider

    override fun cleanup() {}

    override fun rollOver() {}
  }
}

suspend fun <T> collectingFusLogs(logEventFilter: (LogEvent) -> Boolean, action: suspend () -> T): Pair<T, List<LogEvent>> = Disposer.newDisposable().use { lifetime ->
  val (allFusLogs, result) = MockFUSCollector.collectLogEvents(lifetime, action)
  val mlFusLogs: List<LogEvent> = allFusLogs.filter { logEventFilter(it) }
  result to mlFusLogs
}
