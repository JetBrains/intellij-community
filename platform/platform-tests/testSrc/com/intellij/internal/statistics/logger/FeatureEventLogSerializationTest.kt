// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.logger

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.eventLog.SerializationHelper
import com.intellij.internal.statistic.eventLog.filters.LogEventTrueFilter
import com.intellij.internal.statistics.StatisticsTestEventFactory.newEvent
import com.intellij.internal.statistics.StatisticsTestEventFactory.newStateEvent
import com.intellij.internal.statistics.StatisticsTestEventValidator.assertLogEventIsValid
import com.intellij.internal.statistics.StatisticsTestEventValidator.isValid
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeatureEventLogSerializationTest {
  @Test
  fun testEventWithoutData() {
    testEventSerialization(newEvent(groupId = "test.group", eventId = "test.event"), false)
  }

  @Test
  fun testEventWithData() {
    val event = newEvent(groupId = "test.group", eventId = "test.event", data = hashMapOf("param" to 23L))
    testEventSerialization(event, false, "param")
  }

  @Test
  fun testGroupId() {
    testEventSerialization(newEvent(groupId = "test group", eventId = "test.event"), false)
    testEventSerialization(newEvent(groupId = "test.group", eventId = "test.event"), false)
    testEventSerialization(newEvent(groupId = "test-group", eventId = "test.event"), false)
    testEventSerialization(newEvent(groupId = "test-group-42", eventId = "test.event"), false)
  }

  @Test
  fun testGroupIdWithUnicode() {
    testEventSerialization(newEvent(groupId = "group-id\u7A97\u013C", eventId = "event-id"), false)
    testEventSerialization(newEvent(groupId = "group\u5F39id", eventId = "event-id"), false)
    testEventSerialization(newEvent(groupId = "group-id\u02BE", eventId = "event-id"), false)
    testEventSerialization(newEvent(groupId = "��group-id", eventId = "event-id"), false)
  }

  @Test
  fun testEventId() {
    testEventSerialization(newEvent(groupId = "test-group", eventId = "test\tevent"), false)
    testEventSerialization(newEvent(groupId = "test-group", eventId = "test\"event"), false)
    testEventSerialization(newEvent(groupId = "test-group", eventId = "test\'event"), false)
    testEventSerialization(newEvent(groupId = "test-group", eventId = "test event"), false)
    testEventSerialization(newEvent(groupId = "test-group", eventId = "42-testevent"), false)
  }

  @Test
  fun testEventIdWithUnicode() {
    testEventSerialization(newEvent(groupId = "group-id", eventId = "event\u7A97type"), false)
    testEventSerialization(newEvent(groupId = "group-id", eventId = "event\u5F39type\u02BE\u013C"), false)
    testEventSerialization(newEvent(groupId = "group-id", eventId = "event\u02BE"), false)
    testEventSerialization(newEvent(groupId = "group-id", eventId = "\uFFFD\uFFFD\uFFFDevent"), false)
  }

  @Test
  fun testEventIdWithUnicodeAndSystemSymbols() {
    testEventSerialization(newEvent(groupId = "group-id", eventId = "e'vent \u7A97typ\"e"), false)
    testEventSerialization(newEvent(groupId = "group-id", eventId = "ev:e;nt\u5F39ty pe\u02BE\u013C"), false)
  }

  @Test
  fun testEventDataWithTabInName() {
    val event = newEvent(data = hashMapOf("my key" to "value"))
    testEventSerialization(event, false, "my_key")
  }

  @Test
  fun testEventDataWithTabInValue() {
    val event = newEvent(data = hashMapOf("key" to "my value"))
    testEventSerialization(event, false, "key")
  }

  @Test
  fun testEventDataWithUnicodeInName() {
    val event = newEvent(data = hashMapOf("my\uFFFDkey" to "value",
      "some\u013C\u02BE\u037C" to "value",
      "\u013C\u037Ckey" to "value"))
    testEventSerialization(event, false, "my?key", "some???", "??key")
  }

  @Test
  fun testEventDataWithUnicodeInValue() {
    val event = newEvent(groupId = "group-id", eventId = "test-event", data = hashMapOf("first-key" to "my\uFFFDvalue",
      "second-key" to "some\u013C\u02BE\u037C",
      "third-key" to "\u013C\u037Cvalue"))
    testEventSerialization(event, false, "first-key", "second-key", "third-key")
  }

  @Test
  fun testEventDataWithBooleanValue() {
    val event = newEvent(groupId = "group-id", eventId = "test-event", data = hashMapOf("first-key" to true, "second-key" to false))
    testEventSerialization(event, false, "first-key", "second-key")
  }

  @Test
  fun testEventDataWithListInValue() {
    val event = newEvent(groupId = "group-id", eventId = "test-event", data = hashMapOf("key" to listOf("my value", "some value", "value")))

    testEventSerialization(event, false, "key")
  }

  @Test
  fun testEventRequestWithSingleRecord() {
    val events = ArrayList<LogEvent>()
    events.add(newEvent(groupId = "test.group.id", eventId = "first-id"))
    events.add(newEvent(groupId = "test.group.id.2", eventId = "second-id"))

    val request = requestByEvents("recorder-id", "IU", "generated-device-id", false, events)
    val json = ObjectMapper().readTree(LogEventSerializer.toString(request))
    assertLogEventContentIsValid(json, "recorder-id", "IU", "generated-device-id", false, false)
  }

  @Test
  fun testEventRequestWithMultipleRecords() {
    val first = ArrayList<LogEvent>()
    first.add(newEvent(groupId = "test.group.id", eventId = "first-id"))
    first.add(newEvent(groupId = "test.group.id.2", eventId = "second-id"))
    val second = ArrayList<LogEvent>()
    second.add(newEvent(groupId = "test.group.id", eventId = "third-id"))
    second.add(newEvent(groupId = "test.group.id.2", eventId = "forth-id"))
    val records = ArrayList<LogEventRecord>()
    records.add(LogEventRecord(first))
    records.add(LogEventRecord(second))

    val request = LogEventRecordRequest("recorder.id", "IU", "generated-device-id", records, false)
    val json = ObjectMapper().readTree(LogEventSerializer.toString(request))
    assertLogEventContentIsValid(json, "recorder.id", "IU", "generated-device-id", false, false)
  }

  @Test
  fun testEventRequestWithCustomProductCode() {
    val events = ArrayList<LogEvent>()
    events.add(newEvent(groupId = "test.group.id", eventId = "first-id"))
    events.add(newEvent(groupId = "test.group.id.2", eventId = "second-id"))

    val request = requestByEvents("recorder.id", "IC", "generated-device-id", false, events)
    val json = ObjectMapper().readTree(LogEventSerializer.toString(request))
    assertLogEventContentIsValid(json, "recorder.id", "IC", "generated-device-id", false, false)
  }

  @Test
  fun testEventRequestWithCustomDeviceId() {
    val events = ArrayList<LogEvent>()
    events.add(newEvent(groupId = "test.group.id", eventId = "first-id"))
    events.add(newEvent(groupId = "test.group.id.2", eventId = "second-id"))

    val request = requestByEvents("recorder.id", "IU", "my-test-id", false, events)
    val json = ObjectMapper().readTree(LogEventSerializer.toString(request))
    assertLogEventContentIsValid(json, "recorder.id", "IU", "my-test-id", false, false)
  }

  @Test
  fun testEventRequestWithInternal() {
    val events = ArrayList<LogEvent>()
    events.add(newEvent(groupId = "test.group.id", eventId = "first-id"))
    events.add(newEvent(groupId = "test.group.id.2", eventId = "second-id"))

    val request = requestByEvents("recorder.id", "IU", "generated-device-id", true, events)
    val json = ObjectMapper().readTree(LogEventSerializer.toString(request))
    assertLogEventContentIsValid(json, "recorder.id", "IU", "generated-device-id", true, false)
  }

  @Test
  fun testEventCustomRequest() {
    val events = ArrayList<LogEvent>()
    events.add(newEvent(groupId = "test.group.id", eventId = "first-id"))
    events.add(newEvent(groupId = "test.group.id.2", eventId = "second-id"))

    val request = requestByEvents("recorder.id", "PY", "abcdefg", true, events)
    val json = ObjectMapper().readTree(LogEventSerializer.toString(request))
    assertLogEventContentIsValid(json, "recorder.id", "PY", "abcdefg", true, false)
  }

  @Test
  fun testEventRequestWithStateEvents() {
    val events = ArrayList<LogEvent>()
    events.add(newStateEvent(groupId = "config.group.id", eventId = "first-id"))
    events.add(newStateEvent(groupId = "config.group.id.2", eventId = "second-id"))

    val request = requestByEvents("recorder.id", "IU", "generated-device-id", false, events)
    val json = ObjectMapper().readTree(LogEventSerializer.toString(request))
    assertLogEventContentIsValid(json, "recorder.id", "IU", "generated-device-id", false, true)
  }

  @Test
  fun testEventCustomRequestWithStateEvents() {
    val events = ArrayList<LogEvent>()
    events.add(newStateEvent(groupId = "config.group.id", eventId = "first-id"))
    events.add(newStateEvent(groupId = "config.group.id.2", eventId = "second-id"))

    val request = requestByEvents("recorder.id", "IC", "abcdefg", true, events)
    val json = ObjectMapper().readTree(LogEventSerializer.toString(request))
    assertLogEventContentIsValid(json, "recorder.id", "IC", "abcdefg", true, true)
  }

  @Test
  fun testEventWithBuildNumber() {
    testEventSerialization(newEvent(build = "182.2567.1"), false)
  }

  @Test
  fun testStateEvent() {
    testEventSerialization(newStateEvent(groupId = "config.group.id", eventId = "my.config", build = "182.2567.1"), true)
  }

  @Test
  fun testDeserializationNoEvents() {
    testDeserialization()
  }

  @Test
  fun testDeserializationSingleEvent() {
    val events = ArrayList<LogEvent>()
    events.add(newEvent(groupId = "group-id", eventId = "test-event"))

    testDeserialization(events)
  }

  @Test
  fun testDeserializationSingleBatch() {
    val events = ArrayList<LogEvent>()
    events.add(newEvent(groupId = "group-id", eventId = "first"))
    events.add(newEvent(groupId = "group-id-1", eventId = "second"))
    events.add(newEvent(groupId = "group-id-2", eventId = "third"))

    testDeserialization(events)
  }

  @Test
  fun testDeserializationTwoCompleteBatches() {
    val firstBatch = ArrayList<LogEvent>()
    val secondBatch = ArrayList<LogEvent>()
    firstBatch.add(newEvent(groupId = "group-id", eventId = "first"))
    firstBatch.add(newEvent(groupId = "group-id-1", eventId = "second"))
    firstBatch.add(newEvent(groupId = "group-id-2", eventId = "third"))
    secondBatch.add(newEvent(groupId = "group-id", eventId = "fourth"))
    secondBatch.add(newEvent(groupId = "group-id-1", eventId = "fifth"))
    secondBatch.add(newEvent(groupId = "group-id-2", eventId = "sixth"))

    testDeserialization(firstBatch, secondBatch)
  }

  @Test
  fun testDeserializationIncompleteBatch() {
    val firstBatch = ArrayList<LogEvent>()
    val secondBatch = ArrayList<LogEvent>()
    firstBatch.add(newEvent(groupId = "group-id", eventId = "first"))
    firstBatch.add(newEvent(groupId = "group-id-1", eventId = "second"))
    firstBatch.add(newEvent(groupId = "group-id-2", eventId = "third"))
    secondBatch.add(newEvent(groupId = "group-id", eventId = "fourth"))

    testDeserialization(firstBatch, secondBatch)
  }

  @Test
  fun testInvalidEventWithoutSession() {
    val json =
      "{\"build\":\"183.0\",\"bucket\":\"-1\",\"time\":1529428045322," +
        "\"group\":{\"id\":\"lifecycle\",\"version\":\"2\"}," +
        "\"event\":{\"count\":1,\"data\":{},\"id\":\"ideaapp.started\"}" +
      "}"
    val deserialized = LogEventDeserializer(TestDataCollectorDebugLogger).fromString(json)
    assertNull(deserialized)

    assertThrows<NullPointerException> {
      SerializationHelper.deserializeLogEvent(json)
    }
  }

  @Test
  fun testInvalidEventWithTimeAsString() {
    val json =
      "{\"session\":12345,\"build\":\"183.0\",\"bucket\":\"-1\",\"time\":\"current-time\"," +
        "\"group\":{\"id\":\"lifecycle\",\"version\":\"2\"}," +
        "\"event\":{\"count\":1,\"data\":{},\"id\":\"ideaapp.started\"}" +
      "}"
    val deserialized = LogEventDeserializer(TestDataCollectorDebugLogger).fromString(json)
    assertNull(deserialized)

    assertThrows<NullPointerException> {
      SerializationHelper.deserializeLogEvent(json)
    }
  }

  @Test
  fun testInvalidEventWithoutGroup() {
    val json =
      "{\"session\":12345,\"build\":\"183.0\",\"bucket\":\"-1\",\"time\":1529428045322," +
        "\"event\":{\"count\":1,\"data\":{},\"id\":\"ideaapp.started\"}" +
      "}"
    val deserialized = LogEventDeserializer(TestDataCollectorDebugLogger).fromString(json)
    assertNull(deserialized)

    assertThrows<NullPointerException> {
      SerializationHelper.deserializeLogEvent(json)
    }
  }

  @Test
  fun testInvalidEventWithoutEvent() {
    val json =
      "{\"session\":12345,\"build\":\"183.0\",\"bucket\":\"-1\",\"time\":1529428045322," +
        "\"group\":{\"id\":\"lifecycle\",\"version\":\"2\"}" +
      "}"
    val deserialized = LogEventDeserializer(TestDataCollectorDebugLogger).fromString(json)
    assertNull(deserialized)

    assertThrows<NullPointerException> {
      SerializationHelper.deserializeLogEvent(json)
    }
}

  @Test
  fun testEventInOldFormat() {
    val json =
      "{\"session\":\"e4d6236d62f8\",\"bucket\":\"-1\",\"time\":1528885576020," +
        "\"recorder\":{\"id\":\"action-stats\",\"version\":\"2\"}," +
        "\"action\":{\"data\":{},\"id\":\"Run\"}" +
      "}"
    val deserialized = LogEventDeserializer(TestDataCollectorDebugLogger).fromString(json)
    assertNull(deserialized)

    assertThrows<NullPointerException> {
      SerializationHelper.deserializeLogEvent(json)
    }
  }

  @Test
  fun testInvalidJsonEvent() {
    val json = "\"session\":12345,\"build\":\"183.0\",\"bucket\":\"-1\",\"time\":1529428045322}"
    val deserialized = LogEventDeserializer(TestDataCollectorDebugLogger).fromString(json)
    assertNull(deserialized)

    assertThrows<NullPointerException> {
      SerializationHelper.deserializeLogEvent(json)
    }
  }

  @Test
  fun testInvalidEvent() {
    val json = "{\"a\":12345,\"b\":\"183.0\",\"d\":\"-1\"}"
    val deserialized = LogEventDeserializer(TestDataCollectorDebugLogger).fromString(json)
    assertNull(deserialized)

    assertThrows<NullPointerException> {
      SerializationHelper.deserializeLogEvent(json)
    }
  }

  @Test
  fun testEventDataWithObject() {
    val event = newEvent(data = hashMapOf("obj" to mapOf("foo" to "fooValue", "bar" to "barValue")))
    testEventSerialization(event, false, "obj")
  }

  @Test
  fun testEventDataWithObjectList() {
    val event = newEvent(data = hashMapOf("objects" to listOf(mapOf("foo" to "fooValue", "bar" to "barValue"))))
    testEventSerialization(event, false, "objects")
  }

  @Test
  fun testEventDataWithNestedObject() {
    val event = newEvent(data = hashMapOf("obj1" to mapOf("obj2" to mapOf("foo" to "fooValue"))))

    testEventSerialization(event, false, "obj1")
  }

  @Test
  fun testEventDataWithLongInObject() {
    val event = newEvent(data = hashMapOf("obj1" to mapOf("obj2" to mapOf("foo" to 1L))))
    testEventSerialization(event, false, "obj1")
  }

  @Test
  fun testEventDataWithDoubleInObject() {
    val event = newEvent(data = hashMapOf("obj1" to mapOf("obj2" to mapOf("foo" to 2.2))))
    testEventSerialization(event, false, "obj1")
  }

  private fun testDeserialization(vararg batches: List<LogEvent>) {
    val events = ArrayList<LogEvent>()
    val records = ArrayList<LogEventRecord>()
    for (batch in batches) {
      events.addAll(batch)
      records.add(LogEventRecord(batch))
    }
    val expected = LogEventRecordRequest("recorder-id", "IU", "user-id", records, false)

    val log = FileUtil.createTempFile("feature-event-log", ".log")
    try {
      val out = StringBuilder()
      for (event in events) {
        out.append(LogEventSerializer.toString(event)).append("\n")
      }
      FileUtil.writeToFile(log, out.toString())
      val machineId = MachineId("machine-id", 42)
      val actual = LogEventRecordRequest.create(
        log, "recorder-id", "IU", "user-id",
        600, LogEventTrueFilter, false, TestDataCollectorDebugLogger, machineId
      )
      for (record in expected.records) {
        for (event in record.events) {
          LogEventRecordRequest.fillMachineId(event, machineId)
        }
      }
      assertEquals(expected, actual)
    }
    finally {
      FileUtil.delete(log)
    }
  }

  private fun testEventSerialization(event: LogEvent, isState: Boolean, vararg dataOptions: String) {
    val line = LogEventSerializer.toString(event)
    assertLogEventIsValid(ObjectMapper().readTree(line), isState, *dataOptions)

    val deserialized = LogEventDeserializer(TestDataCollectorDebugLogger).fromString(line)
    assertEquals(event, deserialized)

    val customDeserialized = SerializationHelper.deserializeLogEvent(line)
    assertEquals(event, customDeserialized)
  }

  private fun assertLogEventContentIsValid(json: JsonNode, recorder: String, product: String, device: String, internal: Boolean, isState: Boolean) {
    assertTrue(json.get("device").isValueNode)
    assertTrue(isValid(json.get("device").asText()))
    assertEquals(device, json.get("device").asText())

    assertTrue(json.get("product").isValueNode)
    assertTrue(isValid(json.get("product").asText()))
    assertEquals(product, json.get("product").asText())

    assertTrue(json.get("recorder").isValueNode)
    assertTrue(isValid(json.get("recorder").asText()))
    assertEquals(recorder, json.get("recorder").asText())

    assertEquals(internal, json.has("internal"))
    if (internal) {
      assertTrue(json.get("internal").asBoolean())
    }

    assertTrue(json.get("records").isArray)
    val records = json.get("records")
    for (record in records) {
      assertTrue(record.isObject)
      assertTrue(record.get("events").isArray)
      val events = record.get("events")
      for (event in events) {
        assertTrue(event.isObject)
        assertLogEventIsValid(event, isState)
      }
    }
  }

  private fun requestByEvents(recorder: String, product: String, device: String, internal: Boolean, events: List<LogEvent>) : LogEventRecordRequest {
    val records = ArrayList<LogEventRecord>()
    records.add(LogEventRecord(events))
    return LogEventRecordRequest(recorder, product, device, records, internal)
  }
}