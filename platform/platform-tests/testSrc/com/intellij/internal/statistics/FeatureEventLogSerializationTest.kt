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
  fun testEventRecorderWithSpacesEscaping() {
    testEventEscaping(newEvent("recorder id", "test-event-type"), "recorder_id", "test-event-type")
  }

  @Test
  fun testEventActionWithTab() {
    testEventSerialization(newEvent("recorder-id", "event\ttype"), false)
  }

  @Test
  fun testEventActionWithTabEscaping() {
    testEventEscaping(newEvent("recorder-id", "event\ttype"), "recorder-id", "event_type")
  }

  @Test
  fun testEventActionWithQuotes() {
    testEventSerialization(newEvent("recorder-id", "event\"type"), false)
  }

  @Test
  fun testEventActionWithQuotesEscaping() {
    testEventEscaping(newEvent("recorder-id", "event\"type"), "recorder-id", "eventtype")
  }

  @Test
  fun testEventActionWithTabInDataKey() {
    val event = newEvent("recorder-id", "event-type")
    event.event.addData("my key", "value")
    testEventSerialization(event, false, "my_key")
  }

  @Test
  fun testEventActionWithTabInDataKeyEscaping() {
    val event = newEvent("recorder-id", "event-type")
    event.event.addData("my key", "value")

    val data = HashMap<String, Any>()
    data["my_key"] = "value"
    testEventEscaping(event, "recorder-id", "event-type", data)
  }

  @Test
  fun testEventActionWithTabInDataValue() {
    val event = newEvent("recorder-id", "event-type")
    event.event.addData("key", "my value")
    testEventSerialization(event, false, "key")
  }

  @Test
  fun testEventActionWithTabInDataValueEscaping() {
    val event = newEvent("recorder-id", "event-type")
    event.event.addData("key", "my value")

    val data = HashMap<String, Any>()
    data["key"] = "my_value"
    testEventEscaping(event, "recorder-id", "event-type", data)
  }

  @Test
  fun testGroupIdWithUnicode() {
    testEventSerialization(newEvent("recorder-id\u7A97\u013C", "event-id"), false)
    testEventSerialization(newEvent("recorder\u5F39id", "event-id"), false)
    testEventSerialization(newEvent("recorder-id\u02BE", "event-id"), false)
    testEventSerialization(newEvent("��recorder-id", "event-id"), false)
  }

  @Test
  fun testGroupIdWithUnicodeEscaping() {
    testEventEscaping(newEvent("recorder-id\u7A97\u013C", "event-id"), "recorder-id??", "event-id")
    testEventEscaping(newEvent("recorder\u5F39id", "event-id"), "recorder?id", "event-id")
    testEventEscaping(newEvent("recorder-id\u02BE", "event-id"), "recorder-id?", "event-id")
    testEventEscaping(newEvent("��recorder-id", "event-id"), "??recorder-id", "event-id")
  }

  @Test
  fun testEventActionWithUnicode() {
    testEventSerialization(newEvent("recorder-id", "event\u7A97type"), false)
    testEventSerialization(newEvent("recorder-id", "event\u5F39type\u02BE\u013C"), false)
    testEventSerialization(newEvent("recorder-id", "event\u02BE"), false)
    testEventSerialization(newEvent("recorder-id", "\uFFFD\uFFFD\uFFFDevent"), false)
  }

  @Test
  fun testEventActionWithUnicodeEscaping() {
    testEventEscaping(newEvent("recorder-id", "event\u7A97type"), "recorder-id", "event?type")
    testEventEscaping(newEvent("recorder-id", "event弹typeʾļ"), "recorder-id", "event?type??")
    testEventEscaping(newEvent("recorder-id", "eventʾ"), "recorder-id", "event?")
    testEventEscaping(newEvent("recorder-id", "\uFFFD\uFFFD\uFFFDevent"), "recorder-id", "???event")
    testEventEscaping(newEvent("recorder-id", "event-12"), "recorder-id", "event-12")
    testEventEscaping(newEvent("recorder-id", "event@12"), "recorder-id", "event@12")
  }

  @Test
  fun testEventActionWithUnicodeInDataKey() {
    val event = newEvent("recorder-id", "event-type")
    event.event.addData("my\uFFFDkey", "value")
    event.event.addData("some\u013C\u02BE\u037C", "value")
    event.event.addData("\u013C\u037Ckey", "value")
    testEventSerialization(event, false, "my?key", "some???", "??key")
  }

  @Test
  fun testEventActionWithUnicodeInDataKeyEscaping() {
    val event = newEvent("recorder-id", "event-type")
    event.event.addData("my\uFFFDkey", "value")
    event.event.addData("some\u013C\u02BE\u037C", "value")
    event.event.addData("\u013C\u037Ckey", "value")

    val data = HashMap<String, Any>()
    data["my?key"] = "value"
    data["some???"] = "value"
    data["??key"] = "value"
    testEventEscaping(event, "recorder-id", "event-type", data)
  }

  @Test
  fun testEventActionWithUnicodeInDataValue() {
    val event = newEvent("recorder-id", "event-type")
    event.event.addData("first-key", "my\uFFFDvalue")
    event.event.addData("second-key", "some\u013C\u02BE\u037C")
    event.event.addData("third-key", "\u013C\u037Cvalue")
    testEventSerialization(event, false, "first-key", "second-key", "third-key")
  }

  @Test
  fun testEventActionWithUnicodeInDataValueEscaping() {
    val event = newEvent("recorder-id", "event-type")
    event.event.addData("first-key", "my\uFFFDvalue")
    event.event.addData("second-key", "some\u013C\u02BE\u037C")
    event.event.addData("third-key", "\u013C\u037Cvalue")

    val data = HashMap<String, Any>()
    data["first-key"] = "my?value"
    data["second-key"] = "some???"
    data["third-key"] = "??value"
    testEventEscaping(event, "recorder-id", "event-type", data)
  }

  @Test
  fun testEventRequestWithSingleRecord() {
    val events = ArrayList<LogEvent>()
    events.add(newEvent("recorder-id", "first-type"))
    events.add(newEvent("recorder-id-2", "second-type"))

    val request = requestByEvents("IU", "generated-device-id", false, events)
    val json = JsonParser().parse(LogEventSerializer.toString(request)).asJsonObject
    assertLogEventContentIsValid(json, "IU", "generated-device-id", false, false)
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

    val request = requestByRecords("IU", "generated-device-id", false, records)
    val json = JsonParser().parse(LogEventSerializer.toString(request)).asJsonObject
    assertLogEventContentIsValid(json, "IU", "generated-device-id", false, false)
  }

  @Test
  fun testEventRequestWithCustomProductCode() {
    val events = ArrayList<LogEvent>()
    events.add(newEvent("recorder-id", "first-type"))
    events.add(newEvent("recorder-id-2", "second-type"))

    val request = requestByEvents("IC", "generated-device-id", false, events)
    val json = JsonParser().parse(LogEventSerializer.toString(request)).asJsonObject
    assertLogEventContentIsValid(json, "IC", "generated-device-id", false, false)
  }

  @Test
  fun testEventRequestWithCustomDeviceId() {
    val events = ArrayList<LogEvent>()
    events.add(newEvent("recorder-id", "first-type"))
    events.add(newEvent("recorder-id-2", "second-type"))

    val request = requestByEvents("IU", "my-test-id", false, events)
    val json = JsonParser().parse(LogEventSerializer.toString(request)).asJsonObject
    assertLogEventContentIsValid(json, "IU", "my-test-id", false, false)
  }

  @Test
  fun testEventRequestWithInternal() {
    val events = ArrayList<LogEvent>()
    events.add(newEvent("recorder-id", "first-type"))
    events.add(newEvent("recorder-id-2", "second-type"))

    val request = requestByEvents("IU", "generated-device-id", true, events)
    val json = JsonParser().parse(LogEventSerializer.toString(request)).asJsonObject
    assertLogEventContentIsValid(json, "IU", "generated-device-id", true, false)
  }

  @Test
  fun testEventCustomRequest() {
    val events = ArrayList<LogEvent>()
    events.add(newEvent("recorder-id", "first-type"))
    events.add(newEvent("recorder-id-2", "second-type"))

    val request = requestByEvents("PY", "abcdefg", true, events)
    val json = JsonParser().parse(LogEventSerializer.toString(request)).asJsonObject
    assertLogEventContentIsValid(json, "PY", "abcdefg", true, false)
  }

  @Test
  fun testEventRequestWithStateEvents() {
    val events = ArrayList<LogEvent>()
    events.add(newStateEvent("recorder-id", "first-type"))
    events.add(newStateEvent("recorder-id-2", "second-type"))

    val request = requestByEvents("IU", "generated-device-id", false, events)
    val json = JsonParser().parse(LogEventSerializer.toString(request)).asJsonObject
    assertLogEventContentIsValid(json, "IU", "generated-device-id", false, true)
  }

  @Test
  fun testEventCustomRequestWithStateEvents() {
    val events = ArrayList<LogEvent>()
    events.add(newStateEvent("recorder-id", "first-type"))
    events.add(newStateEvent("recorder-id-2", "second-type"))

    val request = requestByEvents("IC", "abcdefg", true, events)
    val json = JsonParser().parse(LogEventSerializer.toString(request)).asJsonObject
    assertLogEventContentIsValid(json, "IC", "abcdefg", true, true)
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
    val expected = requestByRecords("IU", "user-id", false, records)

    val log = FileUtil.createTempFile("feature-event-log", ".log")
    try {
      val out = StringBuilder()
      for (event in all) {
        out.append(LogEventSerializer.toString(event)).append("\n")
      }
      FileUtil.writeToFile(log, out.toString())
      val actual = LogEventRecordRequest.create(log, "IU", "user-id", 600, filter, false)
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
    val expected = requestByRecords("IU", "user-id", false, records)

    val log = FileUtil.createTempFile("feature-event-log", ".log")
    try {
      val out = StringBuilder()
      for (event in events) {
        out.append(LogEventSerializer.toString(event)).append("\n")
      }
      FileUtil.writeToFile(log, out.toString())
      val actual = LogEventRecordRequest.create(log, "IU", "user-id", 600, LogEventTrueFilter, false)
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

  private fun testEventEscaping(event: LogEvent, expectedGroupId: String,
                                expectedEventId: String,
                                expectedData: Map<String, Any> = HashMap()) {
    val json = JsonParser().parse(LogEventSerializer.toString(event)).asJsonObject
    assertLogEventIsValid(json, false)

    assertEquals(expectedGroupId, json.getAsJsonObject("group").get("id").asString)
    assertEquals(expectedEventId, json.getAsJsonObject("event").get("id").asString)

    val obj = json.getAsJsonObject("event").get("data").asJsonObject
    for (option in expectedData.keys) {
      assertTrue(isValid(option))
      assertTrue(obj.get(option).isJsonPrimitive)
      assertEquals(obj.get(option).asString, expectedData[option])
    }
  }

  private fun assertLogEventContentIsValid(json: JsonObject, product: String, device: String, internal: Boolean, isState: Boolean) {
    assertTrue(json.get("device").isJsonPrimitive)
    assertTrue(isValid(json.get("device").asString))
    assertEquals(device, json.get("device").asString)

    assertTrue(json.get("product").isJsonPrimitive)
    assertTrue(isValid(json.get("product").asString))
    assertEquals(product, json.get("product").asString)

    assertTrue(json.get("recorder").isJsonPrimitive)
    assertTrue(isValid(json.get("recorder").asString))
    assertEquals("FUS", json.get("recorder").asString)

    assertEquals(internal, json.has("internal"))
    if (internal) {
      assertTrue(json.get("internal").asBoolean)
    }

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
    assertTrue(isValid(json.get("session").asString))

    assertTrue(json.get("bucket").isJsonPrimitive)
    assertTrue(isValid(json.get("bucket").asString))

    assertTrue(json.get("build").isJsonPrimitive)
    assertTrue(isValid(json.get("build").asString))

    assertTrue(json.get("group").isJsonObject)
    assertTrue(json.getAsJsonObject("group").get("id").isJsonPrimitive)
    assertTrue(json.getAsJsonObject("group").get("version").isJsonPrimitive)
    assertTrue(isValid(json.getAsJsonObject("group").get("id").asString))
    assertTrue(isValid(json.getAsJsonObject("group").get("version").asString))

    assertTrue(json.get("event").isJsonObject)
    assertTrue(json.getAsJsonObject("event").get("id").isJsonPrimitive)
    assertEquals(isState, json.getAsJsonObject("event").has("state"))
    if (isState) {
      assertTrue(json.getAsJsonObject("event").get("state").asBoolean)
    }

    assertEquals(!isState, json.getAsJsonObject("event").has("count"))
    if (!isState) {
      assertTrue(json.getAsJsonObject("event").get("count").asJsonPrimitive.isNumber)
    }

    assertTrue(json.getAsJsonObject("event").get("data").isJsonObject)
    assertTrue(isValid(json.getAsJsonObject("event").get("id").asString))

    val obj = json.getAsJsonObject("event").get("data").asJsonObject
    for (option in dataOptions) {
      assertTrue(isValid(option))
      assertTrue(obj.get(option).isJsonPrimitive)
      assertTrue(isValid(obj.get(option).asString))
    }
  }

  private fun isValid(str : String) : Boolean {
    val noTabsOrSpaces = str.indexOf(" ") == -1 && str.indexOf("\t") == -1 && str.indexOf("\"") == -1
    return noTabsOrSpaces && str.matches("[\\p{ASCII}]*".toRegex())
  }

  private fun requestByEvents(product: String, device: String, internal: Boolean, events: List<LogEvent>) : LogEventRecordRequest {
    val records = ArrayList<LogEventRecord>()
    records.add(LogEventRecord(events))
    return LogEventRecordRequest(product, device, records, internal)
  }

  private fun requestByRecords(product: String, device: String, internal: Boolean, records: List<LogEventRecord>) : LogEventRecordRequest {
    return LogEventRecordRequest(product, device, records, internal)
  }
}