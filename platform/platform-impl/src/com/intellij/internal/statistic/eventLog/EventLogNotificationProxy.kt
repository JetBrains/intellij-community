// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.util.containers.ConcurrentMultiMap

class EventLogNotificationProxy(private val writer: StatisticsEventLogWriter,
                                private val recorderId: String) : StatisticsEventLogWriter {
  override fun log(logEvent: LogEvent) {
    EventLogNotificationService.notifySubscribers(logEvent, recorderId)
    writer.log(logEvent)
  }

  override fun getActiveFile(): EventLogFile? = writer.getActiveFile()

  override fun getFiles(): List<EventLogFile> = writer.getFiles()

  override fun cleanup() = writer.cleanup()

  override fun rollOver() = writer.rollOver()
}

object EventLogNotificationService {
  private val subscribers = ConcurrentMultiMap<String, (LogEvent) -> Unit>()

  fun notifySubscribers(logEvent: LogEvent, recorderId: String) {
    val copyOnWriteArraySet = subscribers[recorderId]
    for (onLogEvent in copyOnWriteArraySet) {
      onLogEvent(logEvent)
    }
  }

  fun subscribe(subscriber: (LogEvent) -> Unit, recorderId: String) {
    subscribers.putValue(recorderId, subscriber)
  }

  fun unsubscribe(subscriber: (LogEvent) -> Unit, recorderId: String) {
    subscribers.remove(recorderId, subscriber)
  }
}