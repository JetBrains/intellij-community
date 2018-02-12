// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.google.gson.JsonParser
import com.intellij.internal.statistic.eventLog.LogEvent
import com.intellij.internal.statistic.eventLog.LogEventSerializer
import org.junit.Test

class FeatureEventLogSerializationTest {

  @Test
  fun testEventWithoutData() {
    val event = LogEvent("session-id", "-1", "recorder-id", "1", "test-event-type")
    assertLogEventIsValid(LogEventSerializer.toString(event))
  }

  @Test
  fun testEventWithData() {
    val event = LogEvent("session-id", "-1", "recorder-id", "1", "test-event-type")
    event.action.data.put("count", 23)
    assertLogEventIsValid(LogEventSerializer.toString(event), "count")
  }

  private fun assertLogEventIsValid(event: String, vararg dataOptions: String) {
    val json = JsonParser().parse(event).asJsonObject
    assert(json.isJsonObject)
    assert(json.get("time").isJsonPrimitive)
    assert(json.get("session").isJsonPrimitive)
    assert(json.get("bucket").isJsonPrimitive)
    assert(json.get("recorder").isJsonObject)
    assert(json.getAsJsonObject("recorder").get("id").isJsonPrimitive)
    assert(json.getAsJsonObject("recorder").get("version").isJsonPrimitive)
    assert(json.get("action").isJsonObject)
    assert(json.getAsJsonObject("action").get("id").isJsonPrimitive)
    assert(json.getAsJsonObject("action").get("data").isJsonObject)
    val obj = json.getAsJsonObject("action").get("data").asJsonObject
    for (option in dataOptions) {
      assert(obj.get(option).isJsonPrimitive)
    }
  }
}