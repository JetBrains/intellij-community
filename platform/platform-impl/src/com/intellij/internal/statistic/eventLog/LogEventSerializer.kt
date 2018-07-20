/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.internal.statistic.eventLog

import com.google.common.reflect.TypeToken
import com.google.gson.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.containers.hash.HashMap
import java.io.OutputStreamWriter
import java.lang.reflect.Type

object LogEventSerializer {
  private val LOG = Logger.getInstance(LogEventSerializer::class.java)
  private val gson = GsonBuilder().registerTypeAdapter(LogEvent::class.java, LogEventJsonDeserializer()).create()

  fun toString(session: LogEventRecordRequest, writer: OutputStreamWriter) {
    writer.write(toString(session))
  }

  /**
   * Serialize request manually so it won't be changed by scrambling
   */
  fun toString(request: LogEventRecordRequest): String {
    val obj = JsonObject()
    obj.addProperty("product", request.product)
    obj.addProperty("user", request.user)

    val records = JsonArray()
    for (record in request.records) {
      val events = JsonArray()
      for (event in record.events) {
        events.add(toJson(event))
      }

      val recordObj = JsonObject()
      recordObj.add("events", events)
      records.add(recordObj)
    }

    obj.add("records", records)
    return obj.toString()
  }

  /**
   * Serialize events manually so it won't be changed by scrambling
   */
  fun toJson(event: LogEvent): JsonObject {
    val obj = JsonObject()
    obj.addProperty("session", event.session)
    obj.addProperty("build", event.build)
    obj.addProperty("bucket", event.bucket)
    obj.addProperty("time", event.time)

    val group = JsonObject()
    group.addProperty("id", event.group.id)
    group.addProperty("version", event.group.version)

    val action = JsonObject()
    if (event.event is LogEventAction) {
      action.addProperty("count", event.event.count)
    }
    action.add("data", gson.toJsonTree(event.event.data))
    action.addProperty("id", event.event.id)

    obj.add("group", group)
    obj.add("event", action)
    return obj
  }

  fun toString(event: LogEvent): String {
    return toJson(event).toString()
  }

  fun fromString(line: String): LogEvent? {
    return try {
      gson.fromJson(line, LogEvent::class.java)
    }
    catch (e : Exception) {
      LOG.trace("Failed deserializing event: '${e.message}'")
      null
    }
  }
}

/**
 * Deserialize events manually so they won't be changed by scrambling
 */
class LogEventJsonDeserializer : JsonDeserializer<LogEvent> {
  @Throws(JsonParseException::class)
  override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): LogEvent {
    val obj = json.asJsonObject
    val session = obj["session"].asString
    val build = obj["build"].asString
    val bucket = obj["bucket"].asString
    val time = obj["time"].asLong

    val group = obj["group"].asJsonObject
    val groupId = group["id"].asString
    val groupVersion = group["version"].asString

    val actionObj = obj["event"].asJsonObject
    val action = createAction(actionObj)
    if (actionObj.has("data")) {
      val dataObj = actionObj.getAsJsonObject("data")
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
    return newLogEvent(session, build, bucket, time, groupId, groupVersion, action)
  }

  fun createAction(obj: JsonObject): LogEventBaseAction {
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