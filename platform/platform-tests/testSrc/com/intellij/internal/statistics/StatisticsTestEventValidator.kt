// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.node.ValueNode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object StatisticsTestEventValidator {
  fun assertLogEventIsValid(json: JsonNode, isState: Boolean, vararg dataOptions: String) {
    assertTrue(json.get("time").isValueNode)

    assertTrue(json.get("session").isValueNode)
    assertTrue(isValid(json.get("session").asString()))

    assertTrue(json.get("bucket").isValueNode)
    assertTrue(isValid(json.get("bucket").asString()))

    assertTrue(json.get("build").isValueNode)
    assertTrue(isValid(json.get("build").asString()))

    assertTrue(json.get("group").isObject)
    assertTrue(json.get("group").get("id").isValueNode)
    assertTrue(json.get("group").get("version").isValueNode)
    assertTrue(isValid(json.get("group").get("id").asString()))
    assertTrue(isValid(json.get("group").get("version").asString()))

    assertTrue(json.get("event").isObject)
    assertTrue(json.get("event").get("id").isValueNode)
    assertEquals(isState, json.get("event").has("state"))
    if (isState) {
      assertTrue(json.get("event").get("state").asBoolean())
    }

    assertEquals(!isState, json.get("event").has("count"))
    if (!isState) {
      assertTrue(json.get("event").get("count").isNumber)
    }

    assertTrue(json.get("event").get("data").isObject)
    assertTrue(isValid(json.get("event").get("id").asString()))

    val obj = json.get("event").get("data")
    validateJsonObject(dataOptions, obj)
  }

  private fun validateJsonObject(dataOptions: Array<out String>, obj: JsonNode) {
    for (option in dataOptions) {
      assertTrue(isValid(option))
      when (val jsonElement = obj.get(option)) {
        is ValueNode -> assertTrue(isValid(jsonElement.asString()))
        is ArrayNode -> {
          for (dataPart in jsonElement) {
            if (dataPart is ObjectNode) {
              validateJsonObject(dataPart.propertyNames().toSet().toTypedArray(), dataPart)
            }
            else {
              assertTrue(isValid(dataPart.asString()))
            }
          }
        }
        is ObjectNode -> {
          validateJsonObject(jsonElement.propertyNames().toSet().toTypedArray(), jsonElement)
        }
      }
    }
  }

  fun isValid(str : String) : Boolean {
    val noTabsOrLineSeparators = str.indexOf("\r") == -1 && str.indexOf("\n") == -1 && str.indexOf("\t") == -1
    val noQuotes = str.indexOf("\"") == -1
    return noTabsOrLineSeparators && noQuotes && str.matches("[\\p{ASCII}]*".toRegex())
  }
}