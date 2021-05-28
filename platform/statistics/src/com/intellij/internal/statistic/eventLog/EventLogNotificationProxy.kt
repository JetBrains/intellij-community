// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class EventLogNotificationProxy(private val writer: StatisticsEventLogWriter,
                                private val recorderId: String) : StatisticsEventLogWriter {
  override fun log(logEvent: LogEvent) {
    EventLogNotificationService.notifySubscribers(logEvent, recorderId)
    writer.log(logEvent)
  }

  override fun getActiveFile(): EventLogFile? = writer.getActiveFile()

  override fun getLogFilesProvider(): EventLogFilesProvider  = writer.getLogFilesProvider()

  override fun cleanup() = writer.cleanup()

  override fun rollOver() = writer.rollOver()

  override fun dispose() = Disposer.dispose(writer)
}

@ApiStatus.Internal
object EventLogNotificationService {
  private val subscribers = MultiMap.createConcurrent<String, (LogEvent) -> Unit>()

  fun notifySubscribers(logEvent: LogEvent, recorderId: String) {
    val copyOnWriteArraySet = subscribers[recorderId]
    for (onLogEvent in copyOnWriteArraySet) {
      onLogEvent(logEvent)
    }
  }

  fun subscribe(subscriber: (LogEvent) -> Unit, recorderId: String) {
    if (!getPluginInfo(subscriber.javaClass).isDevelopedByJetBrains()) {
      return
    }
    subscribers.putValue(recorderId, subscriber)
  }

  fun unsubscribe(subscriber: (LogEvent) -> Unit, recorderId: String) {
    subscribers.remove(recorderId, subscriber)
  }
}