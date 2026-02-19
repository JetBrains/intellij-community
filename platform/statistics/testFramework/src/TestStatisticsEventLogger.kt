// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.EmptyEventLogFilesProvider
import com.intellij.internal.statistic.eventLog.EventLogFile
import com.intellij.internal.statistic.eventLog.EventLogFilesProvider
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.StatisticsEventLogger
import com.intellij.internal.statistic.eventLog.escape
import com.intellij.internal.statistic.eventLog.escapeExceptData
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEventAction
import com.jetbrains.fus.reporting.model.lion3.LogEventGroup
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.function.Consumer

/**
 * [escapeChars] - set false if you want to collect event data with strings containing quotes and/or new lines (important for trace/llmc collectors)
 */
open class TestStatisticsEventLogger(private val session: String = "testSession",
                                private val build: String = "999.999",
                                private val bucket: String = "1",
                                private val recorderVersion: String = "1",
                                private val escapeChars: Boolean = true) : StatisticsEventLogger {
  val logged = CopyOnWriteArrayList<LogEvent>()
  var eventListener: Consumer<LogEvent>? = null

  override fun logAsync(group: EventLogGroup, eventId: String, data: Map<String, Any>, isState: Boolean): CompletableFuture<Void> {
    val eventTime = System.currentTimeMillis()
    val eventAction = LogEventAction(eventId, isState, HashMap(data))
    val eventGroup = LogEventGroup(group.id, group.version.toString())
    val event = LogEvent(session, build, bucket, eventTime, eventGroup, recorderVersion, eventAction).also {
      if (escapeChars) {
        it.escape()
      } else {
        it.escapeExceptData()
      }
    }
    logged.add(event)
    eventListener?.accept(event)
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