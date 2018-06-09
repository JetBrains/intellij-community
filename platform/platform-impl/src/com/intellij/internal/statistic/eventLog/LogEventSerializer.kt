/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.internal.statistic.eventLog

import com.google.common.reflect.TypeToken
import com.google.gson.*
import com.intellij.util.containers.hash.HashMap
import java.io.OutputStreamWriter
import java.lang.reflect.Type

object LogEventSerializer {
  private val gson = GsonBuilder().
    registerTypeAdapter(LogEventBaseAction::class.java, LogEventJsonDeserializer()).
    registerTypeAdapter(LogEventBaseAction::class.java, LogEventJsonSerializer()).create()

  fun toString(session: Any): String {
    return gson.toJson(session)
  }

  fun toString(session: Any, writer: OutputStreamWriter) {
    gson.toJson(session, writer)
  }

  fun fromString(line: String): LogEvent {
    return gson.fromJson(line, LogEvent::class.java)
  }
}

class LogEventJsonDeserializer : JsonDeserializer<LogEventBaseAction> {
  @Throws(JsonParseException::class)
  override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LogEventBaseAction {
    val obj = json.asJsonObject
    val action = createAction(obj)
    if (obj.has("data")) {
      val dataObj = obj.getAsJsonObject("data")
      for ((key, value) in context.deserialize<HashMap<String, Any>>(dataObj, object : TypeToken<HashMap<String, Any>>() {}.type)) {
        if (value is Double && value % 1 == 0.0) {
          val intValue = Math.round(value).toInt()
          action.addData(key, intValue)
        }
        else {
          action.addData(key, value)
        }
      }
    }
    return action
  }

  fun createAction(obj : JsonObject) : LogEventBaseAction {
    val id = obj.get("id").asString
    if (obj.has("count")) {
      val count = obj.get("count").asJsonPrimitive
      if (count.isNumber) {
        return LogEventAction(id, count.asInt)
      }
    }
    return LogStateEventAction(id)
  }
}

class LogEventJsonSerializer : JsonSerializer<LogEventBaseAction> {
  override fun serialize(src: LogEventBaseAction?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
    if (context != null) {
      if (src is LogEventAction) {
        return context.serialize(src, LogEventAction::class.java)
      }
      else if (src is LogStateEventAction) {
        return context.serialize(src, LogStateEventAction::class.java)
      }
    }
    return JsonObject()
  }
}