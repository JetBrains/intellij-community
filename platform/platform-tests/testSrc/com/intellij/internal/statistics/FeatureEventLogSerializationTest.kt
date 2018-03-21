// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.internal.statistic.eventLog.LogEventRecord
import com.intellij.internal.statistic.eventLog.LogEventRecordRequest
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
  fun testEventRequestWithSingleRecord() {
    val events = ArrayList<LogEvent>()
    events.add(LogEvent("session-id", "-1", "recorder-id", "1", "first-type"))
    events.add(LogEvent("session-id", "-1", "recorder-id-2", "1", "second-type"))

    val json = JsonParser().parse(LogEventSerializer.toString(requestByEvents(events))).asJsonObject
    assertLogEventContentIsValid(json)
  }

  @Test
  fun testEventRequestWithMultipleRecords() {
    val first = ArrayList<LogEvent>()
    first.add(LogEvent("session-id", "-1", "recorder-id", "1", "first-type"))
    first.add(LogEvent("session-id", "-1", "recorder-id-2", "1", "second-type"))
    val second = ArrayList<LogEvent>()
    second.add(LogEvent("session-id", "-1", "recorder-id", "1", "third-type"))
    second.add(LogEvent("session-id", "-1", "recorder-id-2", "1", "forth-type"))
    val records = ArrayList<LogEventRecord>()
    records.add(LogEventRecord(first))
    records.add(LogEventRecord(second))

    val json = JsonParser().parse(LogEventSerializer.toString(requestByRecords(records))).asJsonObject
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

    testDeserialization(firstBatch, secondBatch)
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
    val records = ArrayList<LogEventRecord>()
    for (batch in batches) {
      events.addAll(batch)
      records.add(LogEventRecord(batch))
    }
    val expected = requestByRecords(records)

    val log = FileUtil.createTempFile("feature-event-log", ".log")
    try {
      val out = StringBuilder()
      for (event in events) {
        out.append(LogEventSerializer.toString(event)).append("\n")
      }
      FileUtil.writeToFile(log, out.toString())
      val actual = LogEventRecordRequest.create(log, "IU", "user-id", 500)
      assertEquals(expected, actual)
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

    assert(json.get("records").isJsonArray)
    val records = json.get("records").asJsonArray
    for (record in records) {
      assert(record.isJsonObject)
      assert(record.asJsonObject.get("events").isJsonArray)
      val events = record.asJsonObject.get("events").asJsonArray
      for (event in events) {
        assert(event.isJsonObject)
        assertLogEventIsValid(event.asJsonObject)
      }
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

  private fun requestByEvents(events: List<LogEvent>) : LogEventRecordRequest {
    val records = ArrayList<LogEventRecord>()
    records.add(LogEventRecord(events))
    return LogEventRecordRequest("IU", "user-id", records)
  }

  private fun requestByRecords(records: List<LogEventRecord>) : LogEventRecordRequest {
    return LogEventRecordRequest("IU", "user-id", records)
  }
}