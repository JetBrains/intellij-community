// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.components.Service
import com.intellij.util.containers.MultiMap
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service
class EventLogListenersManager {
  private val subscribers = MultiMap.createConcurrent<String, StatisticsEventLogListener>()

  fun notifySubscribers(recorderId: String, validatedEvent: LogEvent, rawEventId: String?, rawData: Map<String, Any>?) {
    val listeners = subscribers[recorderId]
    for (listener in listeners) {
      listener.onLogEvent(validatedEvent, rawEventId, rawData)
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
  /**
   * @param rawEventId Event id before validation.
   * @param rawData Event data before validation.
   *
   * [rawEventId] and [rawData] should be used only for testing purpose, so available only in fus test mode, otherwise will be null.
   */
  fun onLogEvent(validatedEvent: LogEvent, rawEventId: String?, rawData: Map<String, Any>?)
}