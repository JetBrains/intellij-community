// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.internal.statistic.eventLog.LogEventContent
import com.intellij.internal.statistic.eventLog.LogEventSerializer
import com.intellij.openapi.util.io.FileUtil
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
  fun testEventActionWithQuotes() {
    testEventSerialization(LogEvent("session-id", "-1", "recorder-id", "1", "event\"type"))
  }

  @Test
  fun testEventActionWithTagInDataKey() {
    val event = LogEvent("session-id", "-1", "recorder-id", "1", "event-type")
    event.action.addData("my key", "value")
    testEventSerialization(event, "my_key")
  }

  @Test
  fun testEventActionWithTagInDataValue() {
    val event = LogEvent("session-id", "-1", "recorder-id", "1", "event-type")
    event.action.addData("key", "my value")
    testEventSerialization(event, "key")
  }

  @Test
  fun testEventContent() {
    val events = ArrayList<LogEvent>()
    events.add(LogEvent("session-id", "-1", "recorder-id", "1", "first-type"))
    events.add(LogEvent("session-id", "-1", "recorder-id-2", "1", "second-type"))

    val json = JsonParser().parse(LogEventSerializer.toString(eventBatch(events))).asJsonObject
    assertLogEventContentIsValid(json)
  }

  @Test
  fun testDeserializationNoEvents() {
    testDeserialization()
  }

  @Test
  fun testDeserializationSingleEvent() {
    val events = ArrayList<LogEvent>()
    events.add(LogEvent("session-id", "-1", "recorder-id", "1", "test-event-type"))

    testDeserialization(events)
  }

  @Test
  fun testDeserializationSingleBatch() {
    val events = ArrayList<LogEvent>()
    events.add(LogEvent("session-id", "-1", "recorder-id", "1", "first"))
    events.add(LogEvent("session-id", "-1", "recorder-id-1", "1", "second"))
    events.add(LogEvent("session-id", "-1", "recorder-id-2", "1", "third"))

    testDeserialization(events)
  }

  @Test
  fun testDeserializationTwoCompleteBatches() {
    val firstBatch = ArrayList<LogEvent>()
    val secondBatch = ArrayList<LogEvent>()
    firstBatch.add(LogEvent("session-id", "-1", "recorder-id", "1", "first"))
    firstBatch.add(LogEvent("session-id", "-1", "recorder-id-1", "1", "second"))
    firstBatch.add(LogEvent("session-id", "-1", "recorder-id-2", "1", "third"))
    secondBatch.add(LogEvent("session-id", "-1", "recorder-id", "1", "fourth"))
    secondBatch.add(LogEvent("session-id", "-1", "recorder-id-1", "1", "fifth"))
    secondBatch.add(LogEvent("session-id", "-1", "recorder-id-2", "1", "sixth"))

    testDeserialization(firstBatch, secondBatch)//events, expected)
  }

  @Test
  fun testDeserializationIncompleteBatch() {
    val firstBatch = ArrayList<LogEvent>()
    val secondBatch = ArrayList<LogEvent>()
    firstBatch.add(LogEvent("session-id", "-1", "recorder-id", "1", "first"))
    firstBatch.add(LogEvent("session-id", "-1", "recorder-id-1", "1", "second"))
    firstBatch.add(LogEvent("session-id", "-1", "recorder-id-2", "1", "third"))
    secondBatch.add(LogEvent("session-id", "-1", "recorder-id", "1", "fourth"))

    testDeserialization(firstBatch, secondBatch)
  }

  private fun testDeserialization(vararg batches: List<LogEvent>) {
    val events = ArrayList<LogEvent>()
    val expected = ArrayList<LogEventContent>()
    for (batch in batches) {
      events.addAll(batch)
      expected.add(eventBatch(batch))
    }

    val log = FileUtil.createTempFile("feature-event-log", ".log")
    try {
      val out = StringBuilder()
      for (event in events) {
        out.append(LogEventSerializer.toString(event)).append("\n")
      }
      FileUtil.writeToFile(log, out.toString())
      val actual = LogEventContent.create(log, "IU", "user-id", 3)
      assertEquals(expected.size, actual.size)
      for ((index, content) in actual.withIndex()) {
        assertEquals(expected.get(index).events, content.events)
      }
    }
    finally {
      FileUtil.delete(log)
    }
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
    assert(noTabsOrSpacesOrQuotes(json.get("session").asString))

    assert(json.get("bucket").isJsonPrimitive)
    assert(noTabsOrSpacesOrQuotes(json.get("bucket").asString))

    assert(json.get("recorder").isJsonObject)
    assert(json.getAsJsonObject("recorder").get("id").isJsonPrimitive)
    assert(json.getAsJsonObject("recorder").get("version").isJsonPrimitive)
    assert(noTabsOrSpacesOrQuotes(json.getAsJsonObject("recorder").get("id").asString))
    assert(noTabsOrSpacesOrQuotes(json.getAsJsonObject("recorder").get("version").asString))

    assert(json.get("action").isJsonObject)
    assert(json.getAsJsonObject("action").get("id").isJsonPrimitive)
    assert(json.getAsJsonObject("action").get("data").isJsonObject)
    assert(noTabsOrSpacesOrQuotes(json.getAsJsonObject("action").get("id").asString))

    val obj = json.getAsJsonObject("action").get("data").asJsonObject
    for (option in dataOptions) {
      assert(noTabsOrSpacesOrQuotes(option))
      assert(obj.get(option).isJsonPrimitive)
      assert(noTabsOrSpacesOrQuotes(obj.get(option).asString))
    }
  }

  private fun noTabsOrSpacesOrQuotes(str : String) : Boolean {
    return str.indexOf(" ") == -1 && str.indexOf("\t") == -1 && str.indexOf("\"") == -1
  }

  private fun eventBatch(events: List<LogEvent>) : LogEventContent {
    return LogEventContent("IU", "user-id", events)
  }
}