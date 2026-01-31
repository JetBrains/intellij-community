// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")
@file:Internal

package com.intellij.util.xmlb

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jdom.Attribute
import org.jdom.Element
import org.jdom.Namespace
import org.jdom.Text
import org.jetbrains.annotations.ApiStatus.Internal

fun jsonDomToXml(jsonObject: JsonObject): Element {
  val element = Element(true, jsonObject.get("name")!!.jsonPrimitive.content, Namespace.NO_NAMESPACE)
  val content = jsonObject.get("content")?.jsonPrimitive?.content

  val attributes = jsonObject.get("attributes")?.jsonObject
  if (!attributes.isNullOrEmpty()) {
    for ((name, value) in attributes) {
      element.setAttribute(Attribute(true, name, value.jsonPrimitive.content, Namespace.NO_NAMESPACE))
    }
  }

  if (content == null) {
    val children = jsonObject.get("children")?.jsonArray
    if (!children.isNullOrEmpty()) {
      for (child in children) {
        element.addContent(jsonDomToXml(child.jsonObject))
      }
    }
  }
  else {
    element.addContent(Text(true, content))
  }
  return element
}

// used by TBE
fun jdomToJson(element: Element): JsonObject {
  val keys: Array<String?> = arrayOfNulls(4)
  val values: Array<JsonElement?> = arrayOfNulls(4)
  var index = 0

  keys[index] = "name"
  values[index] = JsonPrimitive(element.name)
  index++

  if (element.children.isNotEmpty()) {
    keys[index] = "children"
    values[index] = JsonArray(element.children.map { jdomToJson(it)  })
    index++
  }

  if (element.hasAttributes()) {
    keys[index] = "attributes"
    values[index] = JsonObject(element.attributes.associate { it.name to JsonPrimitive(it.value) })
    index++
  }

  element.content.firstOrNull { it is Text }?.let {
    keys[index] = "content"
    values[index] = JsonPrimitive(it.value)
    index++
  }

  return JsonObject(Object2ObjectArrayMap(keys, values, index))
}

