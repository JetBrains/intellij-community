// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEventAction
import com.jetbrains.fus.reporting.model.lion3.LogEventGroup
import java.io.OutputStreamWriter
import java.lang.reflect.Type
import kotlin.math.roundToLong

private val gson = GsonBuilder().registerTypeAdapter(LogEvent::class.java, LogEventJsonDeserializer()).create()

object LogEventSerializer {
  fun toString(session: LogEventRecordRequest, writer: OutputStreamWriter) {
    writer.write(toString(session))
  }

  /**
   * Serialize request manually so it won't be changed by scrambling
   */
  fun toString(request: LogEventRecordRequest): String {
    val obj = JsonObject()
    obj.addProperty("recorder", request.recorder)
    obj.addProperty("product", request.product)
    obj.addProperty("device", request.device)
    if (request.internal) {
      obj.addProperty("internal", true)
    }

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
    obj.addProperty("recorder_version", event.recorderVersion)
    obj.addProperty("session", event.session)
    obj.addProperty("build", event.build)
    obj.addProperty("bucket", event.bucket)
    obj.addProperty("time", event.time)

    val group = JsonObject()
    group.addProperty("id", event.group.id)
    group.addProperty("version", event.group.version)

    val action = JsonObject()
    if (event.event.state) {
      action.addProperty("state", event.event.state)
    }
    else {
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
}

class LogEventDeserializer(val logger: DataCollectorDebugLogger) {
  fun fromString(line: String): LogEvent? {
    return try {
      gson.fromJson(line, LogEvent::class.java)
    }
    catch (e : Exception) {
      logger.trace("Failed deserializing event: '${e.message}'")
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
    val recorderVersion = obj["recorder_version"].asString
    val session = obj["session"].asString
    val build = obj["build"].asString
    val bucket = obj["bucket"].asString
    val time = obj["time"].asLong

    val group = obj["group"].asJsonObject
    val groupId = group["id"].asString
    val groupVersion = group["version"].asString

    val actionObj = obj["event"].asJsonObject
    val action = createAction(actionObj, context)
    return LogEvent(session, build, bucket, time, LogEventGroup(groupId, groupVersion), recorderVersion, action)
  }

  private fun transformNumbers(value: Any): Any {
    return when {
      value is Double && value % 1 == 0.0 -> value.roundToLong()
      value is List<*> -> value.map { if (it != null) transformNumbers(it) else null }
      value is Map<*, *> -> value.entries.associate { (entryKey, entryValue) ->
        val newValue = if (entryValue != null) transformNumbers(entryValue) else null
        entryKey to newValue
      }
      else -> value
    }
  }

  fun createAction(obj: JsonObject, context: JsonDeserializationContext): LogEventAction {
    val id = obj.get("id").asString
    val isState = obj.has("state") && obj.get("state").asBoolean
    val data = HashMap<String, Any>()
    if (obj.has("data")) {
      val dataObj = obj.getAsJsonObject("data")
      for ((key, value) in context.deserialize<HashMap<String, Any>>(dataObj, object : TypeToken<HashMap<String, Any>>() {}.type)) {
        data[key] = transformNumbers(value)
      }
    }
    if (obj.has("count")) {
      val count = obj.get("count").asJsonPrimitive
      if (count.isNumber) {
        return LogEventAction(id, state = isState, count = count.asInt, data = data)
      }
    }
    return LogEventAction(id, state = isState, data = data)
  }
}