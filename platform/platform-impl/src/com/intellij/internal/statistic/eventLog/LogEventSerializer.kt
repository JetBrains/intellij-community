/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.internal.statistic.eventLog

import com.google.common.reflect.TypeToken
import com.google.gson.*
import com.intellij.util.containers.hash.HashMap
import java.lang.reflect.Type

object LogEventSerializer {
  private val gson = GsonBuilder().registerTypeAdapter(LogEventAction::class.java, LogEventJsonDeserializer()).create()

  fun toString(session: Any): String {
    return gson.toJson(session)
  }

  fun fromString(line: String): LogEvent {
    return gson.fromJson(line, LogEvent::class.java)
  }
}

class LogEventJsonDeserializer : JsonDeserializer<LogEventAction> {
  @Throws(JsonParseException::class)
  override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LogEventAction {
    val obj = json.asJsonObject
    val action = LogEventAction(obj.get("id").asString)
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
}