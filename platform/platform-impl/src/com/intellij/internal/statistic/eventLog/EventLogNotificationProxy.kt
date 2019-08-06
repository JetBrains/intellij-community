// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import java.io.File
import java.util.concurrent.CopyOnWriteArraySet

class EventLogNotificationProxy(private val writer: StatisticsEventLogWriter) : StatisticsEventLogWriter {
  override fun log(logEvent: LogEvent) {
    EventLogNotificationService.notifySubscribers(logEvent)
    writer.log(logEvent)
  }

  override fun getActiveFile(): File? = writer.getActiveFile()

  override fun getFiles(): List<File> = writer.getFiles()

  override fun cleanup() = writer.cleanup()

  override fun rollOver() = writer.rollOver()
}

object EventLogNotificationService {
  private val subscribers = CopyOnWriteArraySet<(LogEvent) -> Unit>()

  fun notifySubscribers(logEvent: LogEvent) {
    for (onLogEvent in subscribers) {
      onLogEvent(logEvent)
    }
  }

  fun subscribe(subscriber: (LogEvent) -> Unit) {
    subscribers.add(subscriber)
  }

  fun unsubscribe(subscriber: (LogEvent) -> Unit) {
    subscribers.remove(subscriber)
  }
}