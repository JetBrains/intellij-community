// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.internal.statistic.eventLog.*
import com.intellij.openapi.util.io.FileUtil
import org.junit.Test
import kotlin.test.assertEquals

class FeatureEventLogSerializationTest {

  @Test
  fun testEventWithoutData() {
    testEventSerialization(LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "test-event-type"))
  }

  @Test
  fun testEventWithData() {
    val event = LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "test-event-type")
    event.event.addData("count", 23)
    testEventSerialization(event, "count")
  }

  @Test
  fun testEventRecorderWithSpaces() {
    testEventSerialization(LogEvent("session-id", "999.9999", "-1", "recorder id", "1", "test-event-type"))
  }

  @Test
  fun testEventActionWithTab() {
    testEventSerialization(LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "event\ttype"))
  }

  @Test
  fun testEventActionWithQuotes() {
    testEventSerialization(LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "event\"type"))
  }

  @Test
  fun testEventActionWithTagInDataKey() {
    val event = LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "event-type")
    event.event.addData("my key", "value")
    testEventSerialization(event, "my_key")
  }

  @Test
  fun testEventActionWithTagInDataValue() {
    val event = LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "event-type")
    event.event.addData("key", "my value")
    testEventSerialization(event, "key")
  }

  @Test
  fun testEventRequestWithSingleRecord() {
    val events = ArrayList<LogEvent>()
    events.add(LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "first-type"))
    events.add(LogEvent("session-id", "999.9999", "-1", "recorder-id-2", "1", "second-type"))

    val json = JsonParser().parse(LogEventSerializer.toString(requestByEvents(events))).asJsonObject
    assertLogEventContentIsValid(json, false)
  }

  @Test
  fun testEventRequestWithMultipleRecords() {
    val first = ArrayList<LogEvent>()
    first.add(LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "first-type"))
    first.add(LogEvent("session-id", "999.9999", "-1", "recorder-id-2", "1", "second-type"))
    val second = ArrayList<LogEvent>()
    second.add(LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "third-type"))
    second.add(LogEvent("session-id", "999.9999", "-1", "recorder-id-2", "1", "forth-type"))
    val records = ArrayList<LogEventRecord>()
    records.add(LogEventRecord(first))
    records.add(LogEventRecord(second))

    val json = JsonParser().parse(LogEventSerializer.toString(requestByRecords(records))).asJsonObject
    assertLogEventContentIsValid(json, false)
  }

  @Test
  fun testEventWithBuildNumber() {
    testEventSerialization(LogEvent("session-id", "182.2567.1", "-1", "recorder-id", "1", "test-event-type"))
  }

  @Test
  fun testDeserializationNoEvents() {
    testDeserialization()
  }

  @Test
  fun testDeserializationSingleEvent() {
    val events = ArrayList<LogEvent>()
    events.add(LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "test-event-type"))

    testDeserialization(events)
  }

  @Test
  fun testDeserializationSingleBatch() {
    val events = ArrayList<LogEvent>()
    events.add(LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "first"))
    events.add(LogEvent("session-id", "999.9999", "-1", "recorder-id-1", "1", "second"))
    events.add(LogEvent("session-id", "999.9999", "-1", "recorder-id-2", "1", "third"))

    testDeserialization(events)
  }

  @Test
  fun testDeserializationTwoCompleteBatches() {
    val firstBatch = ArrayList<LogEvent>()
    val secondBatch = ArrayList<LogEvent>()
    firstBatch.add(LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "first"))
    firstBatch.add(LogEvent("session-id", "999.9999", "-1", "recorder-id-1", "1", "second"))
    firstBatch.add(LogEvent("session-id", "999.9999", "-1", "recorder-id-2", "1", "third"))
    secondBatch.add(LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "fourth"))
    secondBatch.add(LogEvent("session-id", "999.9999", "-1", "recorder-id-1", "1", "fifth"))
    secondBatch.add(LogEvent("session-id", "999.9999", "-1", "recorder-id-2", "1", "sixth"))

    testDeserialization(firstBatch, secondBatch)
  }

  @Test
  fun testDeserializationIncompleteBatch() {
    val firstBatch = ArrayList<LogEvent>()
    val secondBatch = ArrayList<LogEvent>()
    firstBatch.add(LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "first"))
    firstBatch.add(LogEvent("session-id", "999.9999", "-1", "recorder-id-1", "1", "second"))
    firstBatch.add(LogEvent("session-id", "999.9999", "-1", "recorder-id-2", "1", "third"))
    secondBatch.add(LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "fourth"))

    testDeserialization(firstBatch, secondBatch)
  }

  @Test
  fun testEmptyWhitelist() {
    val all = ArrayList<LogEvent>()
    all.add(LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "first"))
    all.add(LogEvent("session-id", "999.9999", "-1", "recorder-id-1", "1", "second"))
    all.add(LogEvent("session-id", "999.9999", "-1", "recorder-id-2", "1", "third"))

    testWhitelist(HashSet(), all, ArrayList())
  }

  @Test
  fun testWhitelist() {
    val first = LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "first")
    val second = LogEvent("session-id", "999.9999", "-1", "recorder-id-1", "1", "second")
    val third = LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "third")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(third)

    val whitelist = HashSet<String>()
    whitelist.add("recorder-id")
    testWhitelist(whitelist, all, filtered)
  }

  @Test
  fun testWhitelistWithMultiGroups() {
    val first = LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "first")
    val second = LogEvent("session-id", "999.9999", "-1", "recorder-id-1", "1", "second")
    val third = LogEvent("session-id", "999.9999", "-1", "recorder-id-2", "1", "third")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(third)

    val whitelist = HashSet<String>()
    whitelist.add("recorder-id")
    whitelist.add("recorder-id-2")
    testWhitelist(whitelist, all, filtered)
  }

  @Test
  fun testWhitelistAll() {
    val first = LogEvent("session-id", "999.9999", "-1", "recorder-id", "1", "first")
    val second = LogEvent("session-id", "999.9999", "-1", "recorder-id-1", "1", "second")
    val third = LogEvent("session-id", "999.9999", "-1", "recorder-id-2", "1", "third")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(second)
    filtered.add(third)

    val whitelist = HashSet<String>()
    whitelist.add("recorder-id")
    whitelist.add("recorder-id-1")
    whitelist.add("recorder-id-2")
    testWhitelist(whitelist, all, filtered)
  }

  private fun testWhitelist(whitelist: Set<String>, all: List<LogEvent>, filtered: List<LogEvent>) {
    val records = ArrayList<LogEventRecord>()
    if (!filtered.isEmpty()) {
      records.add(LogEventRecord(filtered))
    }
    val expected = requestByRecords(records)

    val log = FileUtil.createTempFile("feature-event-log", ".log")
    try {
      val out = StringBuilder()
      for (event in all) {
        out.append(LogEventSerializer.toString(event)).append("\n")
      }
      FileUtil.writeToFile(log, out.toString())
      val actual = LogEventRecordRequest.create(log, "IU", "user-id", 600, LogEventWhitelistFilter(whitelist))
      assertEquals(expected, actual)
    }
    finally {
      FileUtil.delete(log)
    }
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
      val actual = LogEventRecordRequest.create(log, "IU", "user-id", 600, LogEventTrueFilter)
      assertEquals(expected, actual)
    }
    finally {
      FileUtil.delete(log)
    }
  }

  private fun testEventSerialization(event: LogEvent, vararg dataOptions: String) {
    val line = LogEventSerializer.toString(event)
    assertLogEventIsValid(JsonParser().parse(line).asJsonObject, false, *dataOptions)

    val deserialized = LogEventSerializer.fromString(line)
    assertEquals(event, deserialized)
  }

  private fun assertLogEventContentIsValid(json: JsonObject, isState: Boolean) {
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
        assertLogEventIsValid(event.asJsonObject, isState)
      }
    }
  }

  private fun assertLogEventIsValid(json: JsonObject, isState: Boolean, vararg dataOptions: String) {
    assert(json.get("time").isJsonPrimitive)

    assert(json.get("session").isJsonPrimitive)
    assert(noTabsOrSpacesOrQuotes(json.get("session").asString))

    assert(json.get("bucket").isJsonPrimitive)
    assert(noTabsOrSpacesOrQuotes(json.get("bucket").asString))

    assert(json.get("build").isJsonPrimitive)
    assert(noTabsOrSpacesOrQuotes(json.get("build").asString))

    assert(json.get("group").isJsonObject)
    assert(json.getAsJsonObject("group").get("id").isJsonPrimitive)
    assert(json.getAsJsonObject("group").get("version").isJsonPrimitive)
    assert(noTabsOrSpacesOrQuotes(json.getAsJsonObject("group").get("id").asString))
    assert(noTabsOrSpacesOrQuotes(json.getAsJsonObject("group").get("version").asString))

    assert(json.get("event").isJsonObject)
    assert(json.getAsJsonObject("event").get("id").isJsonPrimitive)
    assertEquals(!isState, json.getAsJsonObject("event").get("count").isJsonPrimitive)

    assert(json.getAsJsonObject("event").get("data").isJsonObject)
    assert(noTabsOrSpacesOrQuotes(json.getAsJsonObject("event").get("id").asString))

    val obj = json.getAsJsonObject("event").get("data").asJsonObject
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