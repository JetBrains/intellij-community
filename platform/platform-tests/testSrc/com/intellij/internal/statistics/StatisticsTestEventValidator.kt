// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.google.gson.JsonObject
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object StatisticsTestEventValidator {
  fun assertLogEventIsValid(json: JsonObject, isState: Boolean, vararg dataOptions: String) {
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
      when {
        obj.get(option).isJsonPrimitive -> assertTrue(isValid(obj.get(option).asString))
        obj.get(option).isJsonArray -> {
          for (dataPart in obj.getAsJsonArray(option)) {
            assertTrue(isValid(dataPart.asString))
          }
        }
      }
    }
  }

  fun isValid(str : String) : Boolean {
    val noTabsOrSpaces = str.indexOf(" ") == -1 && str.indexOf("\t") == -1 && str.indexOf("\"") == -1
    return noTabsOrSpaces && str.matches("[\\p{ASCII}]*".toRegex())
  }
}