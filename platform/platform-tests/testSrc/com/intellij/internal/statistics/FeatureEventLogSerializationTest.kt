// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.internal.statistic.eventLog.LogEventSerializer
import org.junit.Test
import kotlin.test.assertEquals

class FeatureEventLogSerializationTest {

  @Test
  fun testEventWithoutData() {
    testEventSerialization(LogEvent("session-id", "-1", "recorder-id", "1", "test-event-type"))
  }

  @Test
  fun testEventWithData() {
    val event = LogEvent("session-id", "-1", "recorder-id", "1", "test-event-type")
    event.action.addData("count", 23)
    testEventSerialization(event, "count")
  }

  @Test
  fun testEventRecorderWithSpaces() {
    testEventSerialization(LogEvent("session-id", "-1", "recorder id", "1", "test-event-type"))
  }

  @Test
  fun testEventActionWithTab() {
    testEventSerialization(LogEvent("session-id", "-1", "recorder-id", "1", "event\ttype"))
  }

  @Test
  fun testEventContent() {
    val events = ArrayList<LogEvent>()
    events.add(LogEvent("session-id", "-1", "recorder-id", "1", "first-type"))
    events.add(LogEvent("session-id", "-1", "recorder-id-2", "1", "second-type"))

    val json = JsonParser().parse(LogEventSerializer.toString(FakeLogEventContent(events))).asJsonObject
    assertLogEventContentIsValid(json)
  }

  private fun testEventSerialization(event: LogEvent, vararg dataOptions: String) {
    val line = LogEventSerializer.toString(event)
    assertLogEventIsValid(JsonParser().parse(line).asJsonObject, *dataOptions)

    val deserialized = LogEventSerializer.fromString(line)
    assertEquals(event, deserialized)
  }

  private fun assertLogEventContentIsValid(json: JsonObject) {
    assert(json.get("user").isJsonPrimitive)
    assert(json.get("product").isJsonPrimitive)

    assert(json.get("events").isJsonArray)
    val events = json.get("events").asJsonArray
    for (event in events) {
      assert(event.isJsonObject)
      assertLogEventIsValid(event.asJsonObject)
    }
  }

  private fun assertLogEventIsValid(json: JsonObject, vararg dataOptions: String) {
    assert(json.get("time").isJsonPrimitive)

    assert(json.get("session").isJsonPrimitive)
    assert(noTabsOrSpaces(json.get("session").asString))

    assert(json.get("bucket").isJsonPrimitive)
    assert(noTabsOrSpaces(json.get("bucket").asString))

    assert(json.get("recorder").isJsonObject)
    assert(json.getAsJsonObject("recorder").get("id").isJsonPrimitive)
    assert(json.getAsJsonObject("recorder").get("version").isJsonPrimitive)
    assert(noTabsOrSpaces(json.getAsJsonObject("recorder").get("id").asString))
    assert(noTabsOrSpaces(json.getAsJsonObject("recorder").get("version").asString))

    assert(json.get("action").isJsonObject)
    assert(json.getAsJsonObject("action").get("id").isJsonPrimitive)
    assert(json.getAsJsonObject("action").get("data").isJsonObject)
    assert(noTabsOrSpaces(json.getAsJsonObject("action").get("id").asString))

    val obj = json.getAsJsonObject("action").get("data").asJsonObject
    for (option in dataOptions) {
      assert(obj.get(option).isJsonPrimitive)
    }
  }

  private fun noTabsOrSpaces(str : String) : Boolean {
    return str.indexOf(" ") == -1 && str.indexOf("\t") == -1
  }

  @Suppress("unused")
  private class FakeLogEventContent(val events: List<LogEvent>) {
    val product = "IU"
    val user = "user-id"
  }
}