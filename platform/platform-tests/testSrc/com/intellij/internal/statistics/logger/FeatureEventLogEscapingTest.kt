// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.logger

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.internal.statistic.eventLog.LogEventSerializer
import com.intellij.internal.statistics.StatisticsTestEventFactory.newEvent
import com.intellij.internal.statistics.StatisticsTestEventValidator.assertLogEventIsValid
import com.intellij.internal.statistics.StatisticsTestEventValidator.isValid
import org.junit.Test
import java.lang.IllegalStateException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FeatureEventLogEscapingTest {

  @Test
  fun testGroupIdEscaping() {
    testGroupIdEscaping(newEvent(groupId = "test group"), "test_group")
    testGroupIdEscaping(newEvent(groupId = "test\tgroup"), "test_group")
    testGroupIdEscaping(newEvent(groupId = "test\"group"), "testgroup")
    testGroupIdEscaping(newEvent(groupId = "test\'group"), "testgroup")
    testGroupIdEscaping(newEvent(groupId = "test.group"), "test.group")
    testGroupIdEscaping(newEvent(groupId = "test-group"), "test-group")
    testGroupIdEscaping(newEvent(groupId = "test-group-42"), "test-group-42")
    testGroupIdEscaping(newEvent(groupId = "42"), "42")
  }

  @Test
  fun testGroupIdWithUnicodeEscaping() {
    testGroupIdEscaping(newEvent(groupId = "group-id\u7A97\u013C"), "group-id??")
    testGroupIdEscaping(newEvent(groupId = "\u7A97\u013C"), "??")
    testGroupIdEscaping(newEvent(groupId = "group\u5F39id"), "group?id")
    testGroupIdEscaping(newEvent(groupId = "group-id\u02BE"), "group-id?")
    testGroupIdEscaping(newEvent(groupId = "��group-id"), "??group-id")
  }

  @Test
  fun testEventEventIdAllowedSymbols() {
    testEventIdEscaping(newEvent(eventId = "test.event"), "test.event")
    testEventIdEscaping(newEvent(eventId = "test-event"), "test-event")
    testEventIdEscaping(newEvent(eventId = "42-test-event"), "42-test-event")
    testEventIdEscaping(newEvent(eventId = "event-12"), "event-12")
    testEventIdEscaping(newEvent(eventId = "event@12"), "event@12")
    testEventIdEscaping(newEvent(eventId = "ev$)en(t*-7ty2p#e!"), "ev$)en(t*-7ty2p#e!")
  }

  @Test
  fun testEventEventIdEscaping() {
    testEventIdEscaping(newEvent(eventId = "test\tevent"), "test event")
    testEventIdEscaping(newEvent(eventId = "test event"), "test event")
    testEventIdEscaping(newEvent(eventId = "t est\tevent"), "t est event")
  }

  @Test
  fun testEventEventIdQuotesEscaping() {
    testEventIdEscaping(newEvent(eventId = "test\"event"), "testevent")
    testEventIdEscaping(newEvent(eventId = "test\'event"), "testevent")
    testEventIdEscaping(newEvent(eventId = "42-test\'event"), "42-testevent")
    testEventIdEscaping(newEvent(eventId = "t\"est'event"), "testevent")
    testEventIdEscaping(newEvent(eventId = "t\"est'event"), "testevent")
  }

  @Test
  fun testEventEventIdSystemSymbolsEscaping() {
    testEventIdEscaping(newEvent(eventId = "t:est;ev,ent"), "t:est;ev,ent")
    testEventIdEscaping(newEvent(eventId = "t:e'st\"e;v\te,n t"), "t:este;v e,n t")
  }

  @Test
  fun testEventIdLineBreaksEscaping() {
    testEventIdEscaping(newEvent(eventId = "e\n\rvent\ntyp\re"), "e  vent typ e")
    testEventIdEscaping(newEvent(eventId = "e\tve  nt\ntyp\re"), "e ve  nt typ e")
  }

  @Test
  fun testEventIdUnicodeEscaping() {
    testEventIdEscaping(newEvent(eventId = "test\uFFFD\uFFFD\uFFFDevent"), "test???event")
    testEventIdEscaping(newEvent(eventId = "\uFFFDevent"), "?event")
    testEventIdEscaping(newEvent(eventId = "test\uFFFD"), "test?")
    testEventIdEscaping(newEvent(eventId = "\u7A97\uFFFD"), "??")
    testEventIdEscaping(newEvent(eventId = "\u7A97\uFFFD\uFFFD\uFFFD"), "????")
    testEventIdEscaping(newEvent(eventId = "\u7A97e:v'e\"n\uFFFD\uFFFD\uFFFDt;t\ty,p e"), "?e:ven???t;t y,p e")
    testEventIdEscaping(newEvent(eventId = "event\u7A97type"), "event?type")
    testEventIdEscaping(newEvent(eventId = "event弹typeʾļ"), "event?type??")
    testEventIdEscaping(newEvent(eventId = "eventʾ"), "event?")
    testEventIdEscaping(newEvent(eventId = "\uFFFD\uFFFD\uFFFDevent"), "???event")
  }

  @Test
  fun testEventDataWithAllSymbolsToEscapeInName() {
    val event = newEvent()
    event.event.addData("my.key", "value")
    event.event.addData("my:", "value")
    event.event.addData(";mykey", "value")
    event.event.addData("another'key", "value")
    event.event.addData("se\"cond\"", "value")
    event.event.addData("a'l\"l;s:y.s,t\tem symbols", "value")

    val expected = HashMap<String, Any>()
    expected["my_key"] = "value"
    expected["my_"] = "value"
    expected["_mykey"] = "value"
    expected["second"] = "value"
    expected["all_s_y_s_t_em_symbols"] = "value"
    testEventDataEscaping(event, expected)
  }

  @Test
  fun testEventDataWithAllSymbolsToEscapeInEventDataValue() {
    val event = newEvent()
    event.event.addData("my.key", "v.alue")
    event.event.addData("my:", "valu:e")
    event.event.addData(";mykey", ";value")
    event.event.addData("another'key", "value'")
    event.event.addData("se\"cond\"", "v\"alue")
    event.event.addData("a'l\"l;s:y.s,t\tem symbols", "fin\"a'l :v;'a\" l,u\te")

    val expected = HashMap<String, Any>()
    expected["my_key"] = "v.alue"
    expected["my_"] = "valu:e"
    expected["_mykey"] = ";value"
    expected["anotherkey"] = "value"
    expected["second"] = "value"
    expected["all_s_y_s_t_em_symbols"] = "final :v;a l,u e"
    testEventDataEscaping(event, expected)
  }

  @Test
  fun testEventDataWithTabInNameEscaping() {
    val event = newEvent()
    event.event.addData("my key", "value")

    val data = HashMap<String, Any>()
    data["my_key"] = "value"
    testEventDataEscaping(event, data)
  }

  @Test
  fun testEventDataWithTabInValueEscaping() {
    val event = newEvent()
    event.event.addData("key", "my value")

    val data = HashMap<String, Any>()
    data["key"] = "my value"
    testEventDataEscaping(event, data)
  }

  @Test
  fun testEventDataWithUnicodeInNameEscaping() {
    val event = newEvent()
    event.event.addData("my\uFFFDkey", "value")
    event.event.addData("some\u013C\u02BE\u037C", "value")
    event.event.addData("\u013C\u037Ckey", "value")

    val data = HashMap<String, Any>()
    data["my?key"] = "value"
    data["some???"] = "value"
    data["??key"] = "value"
    testEventDataEscaping(event, data)
  }

  @Test
  fun testEventDataWithUnicodeInValueEscaping() {
    val event = newEvent()
    event.event.addData("first-key", "my\uFFFDvalue")
    event.event.addData("second-key", "some\u013C\u02BE\u037C")
    event.event.addData("third-key", "\u013C\u037Cvalue")

    val data = HashMap<String, Any>()
    data["first-key"] = "my?value"
    data["second-key"] = "some???"
    data["third-key"] = "??value"
    testEventDataEscaping(event, data)
  }

  @Test
  fun testEventDataWithUnicodeInListValueEscaping() {
    val event = newEvent()
    event.event.addData("key", listOf("my\uFFFDvalue", "some\u013C\u02BE\u037Cvalue", "\u013C\u037Cvalue"))

    val data = HashMap<String, Any>()
    data["key"] = listOf("my?value", "some???value", "??value")
    testEventDataEscaping(event, data)
  }

  @Test
  fun testEventDataWithUnicodeInObjectEscaping() {
    val event = newEvent()
    event.event.addData("key", mapOf("fo\uFFFDo" to "foo\uFFDDValue"))

    val data = HashMap<String, Any>()
    data["key"] = mapOf("fo?o" to "foo?Value")
    testEventDataEscaping(event, data)
  }

  @Test
  fun testEventDataWithUnicodeInObjectListEscaping() {
    val event = newEvent()
    event.event.addData("key", listOf(mapOf("fo\uFFFDo" to "foo\uFFDDValue"), mapOf("ba\uFFFDr" to "bar\uFFDDValue")))

    val data = HashMap<String, Any>()
    data["key"] = listOf(mapOf("fo?o" to "foo?Value"), mapOf("ba?r" to "bar?Value"))
    testEventDataEscaping(event, data)
  }

  @Test
  fun testEventDataWithUnicodeInNestedObjectEscaping() {
    val event = newEvent()
    event.event.addData("key", mapOf("fo\uFFFDo" to mapOf("ba\uFFFDr" to "bar\uFFDDValue")))

    val data = HashMap<String, Any>()
    data["key"] = mapOf("fo?o" to mapOf("ba?r" to "bar?Value"))
    testEventDataEscaping(event, data)
  }

  private fun testEventIdEscaping(event: LogEvent, expectedEventId: String) {
    testEventEscaping(event, "group.id", expectedEventId)
  }

  private fun testGroupIdEscaping(event: LogEvent, expectedGroupId: String) {
    testEventEscaping(event, expectedGroupId, "event.id")
  }

  private fun testEventDataEscaping(event: LogEvent, expectedData: Map<String, Any>) {
    testEventEscaping(event, "group.id", "event.id", expectedData)
  }

  private fun testEventEscaping(event: LogEvent, expectedGroupId: String,
                                expectedEventId: String,
                                expectedData: Map<String, Any> = HashMap()) {
    val json = Gson().fromJson(LogEventSerializer.toString(event), JsonObject::class.java)
    assertLogEventIsValid(json, false)

    assertEquals(expectedGroupId, json.getAsJsonObject("group").get("id").asString)
    assertEquals(expectedEventId, json.getAsJsonObject("event").get("id").asString)

    val obj = json.getAsJsonObject("event").get("data").asJsonObject
    validateJsonObject(expectedData.keys, expectedData, obj)
  }

  private fun validateJsonObject(keys: Set<String>, expectedData: Any?, obj: JsonObject) {
    val expectedMap = expectedData as? Map<*, *>
    assertNotNull(expectedMap)

    for (option in keys) {
      assertTrue(isValid(option))
      val expectedValue = expectedData[option]
      when (val jsonElement = obj.get(option)) {
        is JsonPrimitive -> assertEquals(expectedValue, jsonElement.asString)
        is JsonArray -> {
          for ((dataPart, expected) in jsonElement.zip(expectedValue as Collection<*>)) {
            if (dataPart is JsonObject) {
              validateJsonObject(dataPart.keySet(), expected, dataPart)
            } else {
              assertEquals(expected, dataPart.asString)
            }
          }
        }
        is JsonObject -> {
          validateJsonObject(jsonElement.keySet(), expectedValue, jsonElement)
        }
        else -> throw IllegalStateException("Unsupported type of event data")
      }
    }
  }
}