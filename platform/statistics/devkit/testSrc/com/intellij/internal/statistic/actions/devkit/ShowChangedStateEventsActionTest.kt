// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.actions.devkit

import com.intellij.internal.statistic.devkit.actions.ShowChangedStateEventsAction
import com.intellij.internal.statistic.eventLog.newLogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowChangedStateEventsActionTest {
  @Test
  fun testEquals() {
    assertTrue(ShowChangedStateEventsAction.isEventsEquals(buildLogEvent(), buildLogEvent()))
  }

  @Test
  fun testDifferenceInGroup() {
    assertFalse(ShowChangedStateEventsAction.isEventsEquals(buildLogEvent(group = "groupId1"), buildLogEvent(group = "groupId2")))
    assertFalse(ShowChangedStateEventsAction.isEventsEquals(buildLogEvent(groupVersion = "1"), buildLogEvent(groupVersion = "2")))
  }

  @Test
  fun testDifferenceInEventId() {
    assertFalse(ShowChangedStateEventsAction.isEventsEquals(buildLogEvent(eventId = "event1"), buildLogEvent(eventId = "event2")))
  }

  @Test
  fun testDifferenceInSystemEventData() {
    val event1 = buildLogEvent(data = hashMapOf("key" to "value", "created" to "value1"))
    val event2 = buildLogEvent(data = hashMapOf("key" to "value", "created" to "value2"))
    assertTrue(ShowChangedStateEventsAction.isEventsEquals(event1, event2))
  }

  @Test
  fun testDifferenceInEventData() {
    val event1 = buildLogEvent(data = hashMapOf("key" to "value1"))
    val event2 = buildLogEvent(data = hashMapOf("key" to "value2"))
    assertFalse(ShowChangedStateEventsAction.isEventsEquals(event1, event2))
  }

  @Test
  fun testDifferenceInEventDataSize() {
    val event1 = buildLogEvent(data = hashMapOf("key" to "value", "key1" to "value1"))
    val event2 = buildLogEvent(data = hashMapOf("key" to "value"))
    assertFalse(ShowChangedStateEventsAction.isEventsEquals(event1, event2))
  }

  private fun buildLogEvent(group: String = "group.id",
                            groupVersion: String = "42",
                            eventId: String = "event.id",
                            data: Map<String, Any> = emptyMap()): LogEvent {
    return newLogEvent("9bff3d929780", "202.2171", "176", System.currentTimeMillis(), group, groupVersion, "41", eventId,
      eventData = data)
  }
}