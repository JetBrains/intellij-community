// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEventAction
import com.jetbrains.fus.reporting.model.lion3.LogEventGroup
import tools.jackson.core.JacksonException
import tools.jackson.core.JsonParser
import tools.jackson.core.StreamReadFeature
import tools.jackson.core.exc.StreamReadException
import tools.jackson.databind.DatabindException
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.module.SimpleModule
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.BooleanNode
import tools.jackson.databind.node.DoubleNode
import tools.jackson.databind.node.LongNode
import tools.jackson.databind.node.ObjectNode
import tools.jackson.databind.node.StringNode
import tools.jackson.module.kotlin.kotlinModule
import java.io.OutputStreamWriter
import kotlin.math.roundToLong

object LogEventSerializer {
  fun toString(session: LogEventRecordRequest, writer: OutputStreamWriter) {
    writer.write(toString(session))
  }

  /**
   * Serialize request manually, so it won't be changed by scrambling
   */
  fun toString(request: LogEventRecordRequest): String {
    val mapper = ObjectMapper()
    val obj = mapper.createObjectNode()
    obj.put("recorder", request.recorder)
    obj.put("product", request.product)
    obj.put("device", request.device)
    if (request.internal) {
      obj.put("internal", true)
    }

    val records = mapper.createArrayNode()
    for (record in request.records) {
      val events = mapper.createArrayNode()
      for (event in record.events) {
        events.add(toJson(event))
      }

      val recordObj = mapper.createObjectNode()
      recordObj.putArray("events").addAll(events)
      records.add(recordObj)
    }

    obj.putArray("records").addAll(records)
    return obj.toString()
  }

  /**
   * Serialize events manually, so it won't be changed by scrambling
   */
  private fun toJson(event: LogEvent): ObjectNode {
    val mapper = ObjectMapper()

    val obj = mapper.createObjectNode()
    obj.put("recorder_version", event.recorderVersion)
    obj.put("session", event.session)
    obj.put("build", event.build)
    obj.put("bucket", event.bucket)
    obj.put("time", event.time)

    val group = mapper.createObjectNode()
    group.put("id", event.group.id)
    group.put("version", event.group.version)

    val action = mapper.createObjectNode()
    if (event.event.state) {
      action.put("state", event.event.state)
    }
    else {
      action.put("count", event.event.count)
    }
    action.set("data", mapper.valueToTree(event.event.data))
    action.put("id", event.event.id)

    obj.set("group", group)
    obj.set("event", action)
    return obj
  }

  fun toString(event: LogEvent): String {
    return toJson(event).toString()
  }
}

class LogEventRecordRequestJsonDeserializer : ValueDeserializer<LogEventRecordRequest>() {
  @Throws(JacksonException::class)
  override fun deserialize(jsonParser: JsonParser, context: DeserializationContext): LogEventRecordRequest {
    val node: JsonNode = jsonParser.readValueAsTree()

    val recorder = node.get("recorder").asString()
    val product = node.get("product").asString()
    val device = node.get("device").asString()
    var internal = false
    if (node.has("internal")) {
      internal = node.get("internal").asBoolean()
    }

    val records = node.get("records")
    val logEventRecordList = mutableListOf<LogEventRecord>()

    records.forEach {
      record -> logEventRecordList.add(createLogEventRecord(record))
    }

    return LogEventRecordRequest(recorder, product, device, logEventRecordList, internal)
  }
  private fun createLogEventRecord(record: JsonNode): LogEventRecord {
    val logEventList = mutableListOf<LogEvent>()
    val events = record.get("events")
    events.forEach {
      event -> val logEvent = SerializationHelper.deserializeLogEvent(event.toString())
      logEventList.add(logEvent)
    }
    return LogEventRecord(logEventList)
  }
}

object SerializationHelper {

  private val LOG_EVENT_MAPPER: JsonMapper by lazy {
    val module = SimpleModule()
    module.addDeserializer(LogEvent::class.java, LogEventJsonDeserializer())

    JsonMapper
      .builder()
      .addModule(kotlinModule())
      .addModule(module)
      .enable(DeserializationFeature.USE_LONG_FOR_INTS)
      .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .build()
  }

  private val LOG_EVENT_RECORD_REQUEST_MAPPER: JsonMapper by lazy {
    val module = SimpleModule()
    module.addDeserializer(LogEventRecordRequest::class.java, LogEventRecordRequestJsonDeserializer())

    JsonMapper
      .builder()
      .addModule(kotlinModule())
      .addModule(module)
      .enable(DeserializationFeature.USE_LONG_FOR_INTS)
      .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
      .build()
  }

  /**
   * Method to deserialize JSON content from given JSON content String to LogEvent class using LogEventJsonDeserializer.
   *
   * @throws StreamReadException if underlying input contains invalid content
   *    of type {@link JsonParser} supports (JSON for default case)
   * @throws DatabindException if the input JSON structure does not match structure
   *   expected for result type (or has other mismatch issues)
   */
  @kotlin.jvm.Throws(StreamReadException::class, DatabindException::class)
  fun deserializeLogEvent(json: String): LogEvent {
    return LOG_EVENT_MAPPER.readValue(json, LogEvent::class.java)
  }

  /**
   * Method to deserialize JSON content from given JSON content String to LogEventRecordRequest class
   * using LogEventRecordRequestJsonDeserializer.
   *
   * @throws StreamReadException if underlying input contains invalid content
   *    of type {@link JsonParser} supports (JSON for default case)
   * @throws DatabindException if the input JSON structure does not match structure
   *   expected for result type (or has other mismatch issues)
   */
  @kotlin.jvm.Throws(StreamReadException::class, DatabindException::class)
  fun deserializeLogEventRecordRequest(json: String): LogEventRecordRequest {
    return LOG_EVENT_RECORD_REQUEST_MAPPER.readValue(json, LogEventRecordRequest::class.java)
  }
}

class LogEventDeserializer(val logger: DataCollectorDebugLogger) {
  fun fromString(line: String): LogEvent? {
    return try {
      SerializationHelper.deserializeLogEvent(line)
    }
    catch (e : Exception) {
      logger.trace("Failed deserializing event: '${e.message}'")
      null
    }
  }
}

/**
 * Deserialize events manually, so they won't be changed by scrambling
 */
class LogEventJsonDeserializer : ValueDeserializer<LogEvent>() {
  @Throws(JacksonException::class)
  override fun deserialize(jsonParser: JsonParser, context: DeserializationContext): LogEvent {
    val node: JsonNode = jsonParser.readValueAsTree()

    val recorderVersion = node.get("recorder_version").asString()
    val session = node.get("session").asString()
    val build = node.get("build").asString()
    val bucket = node.get("bucket").asString()
    val time = node.get("time").asLong()

    val group = node.get("group")
    val groupId = group.get("id").asString()
    val groupVersion = group.get("version").asString()

    val actionObj = node.get("event")
    val action = createAction(actionObj)
    return LogEvent(session, build, bucket, time, LogEventGroup(groupId, groupVersion), recorderVersion, action)
  }

  private fun transformData(value: JsonNode): Any {
    return when (value) {
      is StringNode -> value.asString() ?: ""
      is LongNode -> value.longValue()
      is BooleanNode -> value.booleanValue()
      is ArrayNode -> {
        val data = ArrayList<Any>(value.size())
        value.forEach { entryValue ->
          data.add(transformData(entryValue))
        }
        data
      }
      is ObjectNode -> {
        val data = HashMap<Any, Any>()
        value.properties().forEach { (entryKey, entryValue) ->
          val newValue = transformData(entryValue)
          data[entryKey] = newValue
        }
        data
      }
      is DoubleNode -> {
        if (value.doubleValue() % 1 == 0.0) {
          value.doubleValue().roundToLong()
        }
        else {
          value.doubleValue()
        }
      }
      else -> value as Any
    }
  }

  fun createAction(obj: JsonNode): LogEventAction {
    val id = obj.get("id").asString()
    val isState = obj.has("state") && obj.get("state").asBoolean()
    val data = HashMap<String, Any>()
    if (obj.has("data")) {
      val dataObj = obj.get("data")
      dataObj.properties().forEach { (entryKey, entryValue) ->
        data[entryKey] = transformData(entryValue)
      }
    }
    return if (obj.has("count") && obj.get("count").isNumber)
      LogEventAction(id, isState, data, obj.get("count").asInt())
    else
      LogEventAction(id, isState, data)
  }
}
