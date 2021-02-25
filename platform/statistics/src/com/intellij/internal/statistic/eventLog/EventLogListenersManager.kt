// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.components.Service
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service
class EventLogListenersManager {
  private val subscribers = MultiMap.createConcurrent<String, StatisticsEventLogListener>()

  fun notifySubscribers(recorderId: String, validatedEvent: LogEvent, rawGroupId: String, rawEventId: String, rawData: Map<String, Any>) {
    val copyOnWriteArraySet = subscribers[recorderId]
    for (listener in copyOnWriteArraySet) {
      listener.onLogEvent(validatedEvent, rawGroupId, rawEventId, rawData)
    }
  }

  fun subscribe(subscriber: StatisticsEventLogListener, recorderId: String) {
    if (!getPluginInfo(subscriber.javaClass).isDevelopedByJetBrains()) {
      return
    }
    subscribers.putValue(recorderId, subscriber)
  }

  fun unsubscribe(subscriber: StatisticsEventLogListener, recorderId: String) {
    subscribers.remove(recorderId, subscriber)
  }
}

interface StatisticsEventLogListener {
  fun onLogEvent(validatedEvent: LogEvent, rawGroupId: String, rawEventId: String, rawData: Map<String, Any>)
}