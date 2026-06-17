// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.dispatcher

import com.intellij.internal.statistic.eventLog.DefaultEventLogFilesProvider
import com.intellij.internal.statistic.eventLog.EventLogFile
import com.intellij.internal.statistic.eventLog.EventLogFilesProvider
import com.intellij.internal.statistic.eventLog.StatisticsEventLogWriter
import com.intellij.openapi.util.Disposer
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Bridges the legacy [StatisticsEventLogWriter] chain (merge → throttle → write) onto the new [IntellijReportDispatcher].
 *
 * `log` forwards events to the dispatcher's [PersistentQueue][com.jetbrains.fus.reporting.defaults.dispatcher.PersistentQueue],
 * which handles file rotation and serialization via [IntellijFusJsonSerializer]. File-listing helpers point at the same
 * directory the queue writes to, so the external uploader and devtool actions (`Show Active Log File`, etc.) continue to work
 * unchanged.
 */
@ApiStatus.Internal
class DispatcherBackedEventLogWriter(
  private val dispatcher: IntellijReportDispatcher,
  private val eventLogDir: Path,
) : StatisticsEventLogWriter {

  override fun log(logEvent: LogEvent) {
    // The caller is StatisticsFileEventLogger.logExecutor (single-threaded, bounded). Suspending briefly here preserves event
    // order while still letting PersistentQueue do its file I/O on its own coroutine context.
    runBlocking { dispatcher.queueEvent(logEvent) }
  }

  override fun getActiveFile(): EventLogFile? {
    val active = activeFileName() ?: return null
    return EventLogFile(eventLogDir.resolve(active).toFile())
  }

  override fun getLogFilesProvider(): EventLogFilesProvider = DefaultEventLogFilesProvider(eventLogDir) { activeFileName() }

  override fun cleanup() {
    // Best-effort: remove all .log files in the recorder's directory. PersistentQueue will recreate as needed on the next write.
    eventLogDir.toFile().listFiles()?.filter { it.name.endsWith(".log") || it.name.endsWith(".log.meta") }?.forEach { it.delete() }
  }

  override fun rollOver() {
    // PersistentQueue handles rotation on size threshold internally; no explicit forced-rollover API is exposed today.
    // Manual rollover (e.g. from devtool actions) is therefore a no-op for now.
  }

  override fun dispose() {
    Disposer.dispose(this)
  }

  // PersistentQueue names active files `<unique>[-<suffix>]-<buildType>.log`; the most recently modified `.log` file is the
  // one currently being appended to.
  private fun activeFileName(): String? =
    eventLogDir.toFile().listFiles()?.filter { it.name.endsWith(".log") }?.maxByOrNull { it.lastModified() }?.name
}
