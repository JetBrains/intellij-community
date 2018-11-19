// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.internal.statistic.eventLog.*
import com.intellij.openapi.util.io.FileUtil
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureEventLogSerializationTest {

  @Test
  fun testEventWithoutData() {
    testEventSerialization(newEvent("recorder-id", "test-event-type"), false)
  }

  @Test
  fun testEventWithData() {
    val event = newEvent("recorder-id","test-event-type")
    event.event.addData("param", 23L)
    testEventSerialization(event, false, "param")
  }

  @Test
  fun testEventRecorderWithSpaces() {
    testEventSerialization(newEvent("recorder id", "test-event-type"), false)
  }

  @Test
  fun testEventActionWithTab() {
    testEventSerialization(newEvent("recorder-id", "event\ttype"), false)
  }

  @Test
  fun testEventActionWithQuotes() {
    testEventSerialization(newEvent("recorder-id", "event\"type"), false)
  }

  @Test
  fun testEventActionWithTagInDataKey() {
    val event = newEvent("recorder-id", "event-type")
    event.event.addData("my key", "value")
    testEventSerialization(event, false, "my_key")
  }

  @Test
  fun testEventActionWithTagInDataValue() {
    val event = newEvent("recorder-id", "event-type")
    event.event.addData("key", "my value")
    testEventSerialization(event, false, "key")
  }

  @Test
  fun testEventRequestWithSingleRecord() {
    val events = ArrayList<LogEvent>()
    events.add(newEvent("recorder-id", "first-type"))
    events.add(newEvent("recorder-id-2", "second-type"))

    val json = JsonParser().parse(LogEventSerializer.toString(requestByEvents(events))).asJsonObject
    assertLogEventContentIsValid(json, false)
  }

  @Test
  fun testEventRequestWithMultipleRecords() {
    val first = ArrayList<LogEvent>()
    first.add(newEvent("recorder-id", "first-type"))
    first.add(newEvent("recorder-id-2", "second-type"))
    val second = ArrayList<LogEvent>()
    second.add(newEvent("recorder-id", "third-type"))
    second.add(newEvent("recorder-id-2", "forth-type"))
    val records = ArrayList<LogEventRecord>()
    records.add(LogEventRecord(first))
    records.add(LogEventRecord(second))

    val json = JsonParser().parse(LogEventSerializer.toString(requestByRecords(records))).asJsonObject
    assertLogEventContentIsValid(json, false)
  }

  @Test
  fun testEventWithBuildNumber() {
    testEventSerialization(newEvent("recorder-id", "test-event-type", build="182.2567.1"), false)
  }

  @Test
  fun testStateEvent() {
    testEventSerialization(newStateEvent("config-recorder", "my-config", build="182.2567.1"), true)
  }

  @Test
  fun testDeserializationNoEvents() {
    testDeserialization()
  }

  @Test
  fun testDeserializationSingleEvent() {
    val events = ArrayList<LogEvent>()
    events.add(newEvent("recorder-id", "test-event-type"))

    testDeserialization(events)
  }

  @Test
  fun testDeserializationSingleBatch() {
    val events = ArrayList<LogEvent>()
    events.add(newEvent("recorder-id", "first"))
    events.add(newEvent("recorder-id-1", "second"))
    events.add(newEvent("recorder-id-2", "third"))

    testDeserialization(events)
  }

  @Test
  fun testDeserializationTwoCompleteBatches() {
    val firstBatch = ArrayList<LogEvent>()
    val secondBatch = ArrayList<LogEvent>()
    firstBatch.add(newEvent("recorder-id", "first"))
    firstBatch.add(newEvent("recorder-id-1", "second"))
    firstBatch.add(newEvent("recorder-id-2", "third"))
    secondBatch.add(newEvent("recorder-id", "fourth"))
    secondBatch.add(newEvent("recorder-id-1", "fifth"))
    secondBatch.add(newEvent("recorder-id-2", "sixth"))

    testDeserialization(firstBatch, secondBatch)
  }

  @Test
  fun testDeserializationIncompleteBatch() {
    val firstBatch = ArrayList<LogEvent>()
    val secondBatch = ArrayList<LogEvent>()
    firstBatch.add(newEvent("recorder-id", "first"))
    firstBatch.add(newEvent("recorder-id-1", "second"))
    firstBatch.add(newEvent("recorder-id-2", "third"))
    secondBatch.add(newEvent("recorder-id", "fourth"))

    testDeserialization(firstBatch, secondBatch)
  }

  @Test
  fun testEmptyWhitelist() {
    val all = ArrayList<LogEvent>()
    all.add(newEvent("recorder-id", "first"))
    all.add(newEvent("recorder-id-1", "second"))
    all.add(newEvent("recorder-id-2", "third"))

    testWhitelistFilter(HashSet(), all, ArrayList())
  }

  @Test
  fun testWhitelist() {
    val first = newEvent("recorder-id", "first")
    val second = newEvent("recorder-id-1", "second")
    val third = newEvent("recorder-id", "third")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(third)

    val whitelist = HashSet<String>()
    whitelist.add("recorder-id")
    testWhitelistFilter(whitelist, all, filtered)
  }

  @Test
  fun testWhitelistWithMultiGroups() {
    val first = newEvent("recorder-id", "first")
    val second = newEvent("recorder-id-1", "second")
    val third = newEvent("recorder-id-2", "third")

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
    testWhitelistFilter(whitelist, all, filtered)
  }

  @Test
  fun testWhitelistAll() {
    val first = newEvent("recorder-id", "first")
    val second = newEvent("recorder-id-1", "second")
    val third = newEvent("recorder-id-2", "third")

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
    testWhitelistFilter(whitelist, all, filtered)
  }

  @Test
  fun testPartialSnapshotBuildsFilter() {
    val first = newEvent("recorder-id", "first", build="999.9999")
    val second = newEvent("recorder-id-1", "second", build="999.0")
    val third = newEvent("recorder-id", "third", build="999.9999")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)
    filtered.add(third)

    testSnapshotBuilderFilter(all, filtered)
  }

  @Test
  fun testNoneSnapshotBuildsFilter() {
    val first = newEvent("recorder-id", "first", build="999.9999")
    val second = newEvent("recorder-id-1", "second", build="999.01")
    val third = newEvent("recorder-id", "third", build="999.9999")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    testSnapshotBuilderFilter(all, all)
  }

  @Test
  fun testAllSnapshotBuildsFilter() {
    val first = newEvent("recorder-id", "first", build="999.00")
    val second = newEvent("recorder-id-1", "second", build="999.0")
    val third = newEvent("recorder-id", "third", build="999.0")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    testSnapshotBuilderFilter(all, ArrayList())
  }

  @Test
  fun testSnapshotBuildsAndWhitelistFilter() {
    val first = newEvent("recorder-id", "first", build="999.9999")
    val second = newEvent("recorder-id-1", "second", build="999.9999")
    val third = newEvent("recorder-id", "third", build="999.0")

    val all = ArrayList<LogEvent>()
    all.add(first)
    all.add(second)
    all.add(third)
    val filtered = ArrayList<LogEvent>()
    filtered.add(first)

    val whitelist = HashSet<String>()
    whitelist.add("recorder-id")
    testWhitelistAndSnapshotBuildFilter(whitelist, all, filtered)
  }

  @Test
  fun testInvalidEventWithoutSession() {
    val json =
      "{\"build\":\"183.0\",\"bucket\":\"-1\",\"time\":1529428045322," +
        "\"group\":{\"id\":\"lifecycle\",\"version\":\"2\"}," +
        "\"event\":{\"count\":1,\"data\":{},\"id\":\"ideaapp.started\"}" +
      "}"
    val deserialized = LogEventSerializer.fromString(json)
    assertNull(deserialized)
  }

  @Test
  fun testInvalidEventWithTimeAsString() {
    val json =
      "{\"session\":12345,\"build\":\"183.0\",\"bucket\":\"-1\",\"time\":\"current-time\"," +
        "\"group\":{\"id\":\"lifecycle\",\"version\":\"2\"}," +
        "\"event\":{\"count\":1,\"data\":{},\"id\":\"ideaapp.started\"}" +
      "}"
    val deserialized = LogEventSerializer.fromString(json)
    assertNull(deserialized)
  }

  @Test
  fun testInvalidEventWithoutGroup() {
    val json =
      "{\"session\":12345,\"build\":\"183.0\",\"bucket\":\"-1\",\"time\":1529428045322," +
        "\"event\":{\"count\":1,\"data\":{},\"id\":\"ideaapp.started\"}" +
      "}"
    val deserialized = LogEventSerializer.fromString(json)
    assertNull(deserialized)
  }

  @Test
  fun testInvalidEventWithoutEvent() {
    val json =
      "{\"session\":12345,\"build\":\"183.0\",\"bucket\":\"-1\",\"time\":1529428045322," +
        "\"group\":{\"id\":\"lifecycle\",\"version\":\"2\"}" +
      "}"
    val deserialized = LogEventSerializer.fromString(json)
    assertNull(deserialized)
  }

  @Test
  fun testEventInOldFormat() {
    val json =
      "{\"session\":\"e4d6236d62f8\",\"bucket\":\"-1\",\"time\":1528885576020," +
        "\"recorder\":{\"id\":\"action-stats\",\"version\":\"2\"}," +
        "\"action\":{\"data\":{},\"id\":\"Run\"}" +
      "}"
    val deserialized = LogEventSerializer.fromString(json)
    assertNull(deserialized)
  }

  @Test
  fun testInvalidJsonEvent() {
    val json = "\"session\":12345,\"build\":\"183.0\",\"bucket\":\"-1\",\"time\":1529428045322}"
    val deserialized = LogEventSerializer.fromString(json)
    assertNull(deserialized)
  }

  @Test
  fun testInvalidEvent() {
    val json = "{\"a\":12345,\"b\":\"183.0\",\"d\":\"-1\"}"
    val deserialized = LogEventSerializer.fromString(json)
    assertNull(deserialized)
  }

  private fun testWhitelistFilter(whitelist: Set<String>, all: List<LogEvent>, filtered: List<LogEvent>) {
    testEventLogFilter(all, filtered, LogEventWhitelistFilter(whitelist))
  }

  private fun testWhitelistAndSnapshotBuildFilter(whitelist: Set<String>, all: List<LogEvent>, filtered: List<LogEvent>) {
    testEventLogFilter(all, filtered, LogEventCompositeFilter(LogEventWhitelistFilter(whitelist), LogEventSnapshotBuildFilter))
  }

  private fun testSnapshotBuilderFilter(all: List<LogEvent>, filtered: List<LogEvent>) {
    testEventLogFilter(all, filtered, LogEventSnapshotBuildFilter)
  }

  private fun testEventLogFilter(all: List<LogEvent>, filtered: List<LogEvent>, filter : LogEventFilter) {
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
      val actual = LogEventRecordRequest.create(log, "IU", "user-id", 600, filter)
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

  private fun testEventSerialization(event: LogEvent, isState: Boolean, vararg dataOptions: String) {
    val line = LogEventSerializer.toString(event)
    assertLogEventIsValid(JsonParser().parse(line).asJsonObject, isState, *dataOptions)

    val deserialized = LogEventSerializer.fromString(line)
    assertEquals(event, deserialized)
  }

  private fun assertLogEventContentIsValid(json: JsonObject, isState: Boolean) {
    assertTrue(json.get("user").isJsonPrimitive)
    assertTrue(json.get("product").isJsonPrimitive)

    assertTrue(json.get("records").isJsonArray)
    val records = json.get("records").asJsonArray
    for (record in records) {
      assertTrue(record.isJsonObject)
      assertTrue(record.asJsonObject.get("events").isJsonArray)
      val events = record.asJsonObject.get("events").asJsonArray
      for (event in events) {
        assertTrue(event.isJsonObject)
        assertLogEventIsValid(event.asJsonObject, isState)
      }
    }
  }

  private fun assertLogEventIsValid(json: JsonObject, isState: Boolean, vararg dataOptions: String) {
    assertTrue(json.get("time").isJsonPrimitive)

    assertTrue(json.get("session").isJsonPrimitive)
    assertTrue(noTabsOrSpacesOrQuotes(json.get("session").asString))

    assertTrue(json.get("bucket").isJsonPrimitive)
    assertTrue(noTabsOrSpacesOrQuotes(json.get("bucket").asString))

    assertTrue(json.get("build").isJsonPrimitive)
    assertTrue(noTabsOrSpacesOrQuotes(json.get("build").asString))

    assertTrue(json.get("group").isJsonObject)
    assertTrue(json.getAsJsonObject("group").get("id").isJsonPrimitive)
    assertTrue(json.getAsJsonObject("group").get("version").isJsonPrimitive)
    assertTrue(noTabsOrSpacesOrQuotes(json.getAsJsonObject("group").get("id").asString))
    assertTrue(noTabsOrSpacesOrQuotes(json.getAsJsonObject("group").get("version").asString))

    assertTrue(json.get("event").isJsonObject)
    assertTrue(json.getAsJsonObject("event").get("id").isJsonPrimitive)
    assertEquals(!isState, json.getAsJsonObject("event").has("count"))
    if (!isState) {
      assertTrue(json.getAsJsonObject("event").get("count").asJsonPrimitive.isNumber)
    }

    assertTrue(json.getAsJsonObject("event").get("data").isJsonObject)
    assertTrue(noTabsOrSpacesOrQuotes(json.getAsJsonObject("event").get("id").asString))

    val obj = json.getAsJsonObject("event").get("data").asJsonObject
    for (option in dataOptions) {
      assertTrue(noTabsOrSpacesOrQuotes(option))
      assertTrue(obj.get(option).isJsonPrimitive)
      assertTrue(noTabsOrSpacesOrQuotes(obj.get(option).asString))
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