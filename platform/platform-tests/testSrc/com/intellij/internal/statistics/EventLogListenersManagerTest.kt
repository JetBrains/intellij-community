// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.EventLogListenersManager
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.StatisticsEventLogListener
import com.intellij.openapi.components.service
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEventAction
import com.jetbrains.fus.reporting.model.lion3.LogEventGroup
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

@TestApplication
class EventLogListenersManagerTest {
  @Test
  fun `raw data and jcp payload are withheld from non-jcp listeners outside test mode`() {
    val manager = service<EventLogListenersManager>()
    val recorderId = "JCP_TEST_RECORDER"

    val captured = mutableListOf<Triple<LogEvent, String?, Map<String, Any>?>>()
    val listener = object : StatisticsEventLogListener {
      override fun onLogEvent(validatedEvent: LogEvent, rawEventId: String?, rawData: Map<String, Any>?) {
        captured += Triple(validatedEvent, rawEventId, rawData)
      }
    }

    manager.subscribe(listener, recorderId)
    try {
      val event = logEvent()
      manager.notifySubscribers(
        recorderId, event,
        rawEventId = "raw.id",
        rawData = mapOf(FeatureUsageData.JCP_DATA_KEY to mapOf("loc" to "42"), "foo" to "bar"),
        isFromLocalRecorder = false,
      )

      val (forwardedEvent, rawEventId, rawData) = captured.single()
      assertSame(event, forwardedEvent)
      assertNull(rawEventId, "raw event id must not leak outside test mode")
      assertNull(rawData, "raw data (including the JCP payload) must not leak to non-JCP listeners")
    }
    finally {
      manager.unsubscribe(listener, recorderId)
    }
  }

  private fun logEvent(): LogEvent =
    LogEvent(
      session = "session", build = "build", bucket = "0",
      time = 0L, group = LogEventGroup("group.id", "1"),
      recorderVersion = "1", event = LogEventAction("event.id", false, HashMap<String, Any>()),
    )
}
