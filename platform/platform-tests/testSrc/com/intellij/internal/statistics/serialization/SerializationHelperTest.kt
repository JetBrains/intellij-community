// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.serialization

import com.intellij.internal.statistic.config.SerializationHelper
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.eventLog.LogEventRecord
import com.intellij.internal.statistic.eventLog.LogEventRecordRequest
import com.intellij.internal.statistic.eventLog.LogEventSerializer
import com.intellij.internal.statistic.eventLog.events.scheme.*
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEventAction
import com.jetbrains.fus.reporting.model.lion3.LogEventGroup
import com.jetbrains.fus.reporting.model.metadata.EventGroupRemoteDescriptors
import org.junit.jupiter.api.Assertions
import java.io.File
import java.io.StringWriter

internal class SerializationHelperTest : BasePlatformTestCase() {
  private fun getTestDataRoot() = PlatformTestUtil.getPlatformTestDataPath() + "fus/serialization/"

  fun testSerializationGroupRemoteRule() {
    val enums = mapOf("__event_id1" to setOf("loading.config.failed", "logs.send", "metadata.loaded"),
                      "__event_id2" to setOf("metadata.updated", "metadata.load.failed", "metadata.update.failed"))

    val eventData = mapOf("code" to setOf("{regexp#integer}"),
                          "error" to setOf("{util#class_name}",
                                           "{enum:EMPTY_SERVICE_URL|UNREACHABLE_SERVICE|EMPTY_RESPONSE_BODY|ERROR_ON_LOAD}",
                                           "{enum:EMPTY_CONTENT|INVALID_JSON|UNKNOWN}"),
                          "version" to setOf("{regexp#version}"))

    val regexps = mapOf("count" to "\\d+K?M?\\+?",
                        "float" to "-?\\d+\\.\\d+(E\\-?\\d+)?",
                        "integer" to "-?\\d+(\\+)?")

    val validationRule = EventGroupRemoteDescriptors.GroupRemoteRule()
    validationRule.event_id = setOf("{enum#__event_id}", "{enum:sessionFinished|searchRestarted}")
    validationRule.enums = enums
    validationRule.event_data = eventData
    validationRule.regexps = regexps

    val serializationText = SerializationHelper.serialize(validationRule)
    val realText = File(getTestDataRoot() + "SerializationGroupRemoteRule.json").readText(Charsets.UTF_8)

    Assertions.assertEquals(realText, serializationText)
  }

  fun testDeserializationGroupRemoteRule() {
    val validationRule = File(getTestDataRoot() + "SerializationGroupRemoteRule.json").readText(Charsets.UTF_8)

    val deserializationObject = SerializationHelper.deserialize(validationRule, EventGroupRemoteDescriptors.GroupRemoteRule::class.java)
    val serializationText = SerializationHelper.serialize(deserializationObject)

    Assertions.assertEquals(validationRule, serializationText)
  }

  fun testSerializationGroupDescriptor() {
    val filedDescriptor = FieldDescriptor("plugin", setOf("{util#class_name}", "{util#plugin}"))
    val eventSchemeDescriptor = EventDescriptor("testEvent", setOf(filedDescriptor, filedDescriptor))
    val groupDescriptor = GroupDescriptor("testId", "counter", 1, setOf(eventSchemeDescriptor, eventSchemeDescriptor),
                                          "classNameTest", "recorderTest", PluginSchemeDescriptor("pluginIdTest"))
    val serializationText = SerializationHelper.serialize(groupDescriptor)
    val realText = File(getTestDataRoot() + "SerializationGroupDescriptor.json").readText(Charsets.UTF_8)

    Assertions.assertEquals(realText, serializationText)
  }

  fun testDeserializationGroupDescriptor() {
    val groupDescriptor = File(getTestDataRoot() + "SerializationGroupDescriptor.json").readText(Charsets.UTF_8)

    val deserializationObject = SerializationHelper.deserialize(groupDescriptor, GroupDescriptor::class.java)
    val serializationText = SerializationHelper.serialize(deserializationObject)

    Assertions.assertEquals(groupDescriptor, serializationText)
  }

  fun testSerializationFeatureUsageData() {
    val data = FeatureUsageData("FUS")
    data.addData("durationMs", 1)
    data.addData("version", "unknown")
    data.addData("file_path", "testData/Serialization.json")

    val serializationText = SerializationHelper.serializeToSingleLine(data.build())
    val realText = File(getTestDataRoot() + "SerializationFeatureUsageData.json").readText(Charsets.UTF_8)

    Assertions.assertEquals(realText, serializationText)
  }

  fun testSerializationEventGroupRemoteDescriptors() {
    val eventGroupRemoteDescriptors = EventGroupRemoteDescriptors()

    eventGroupRemoteDescriptors.version = "1"
    val validationRule = EventGroupRemoteDescriptors.GroupRemoteRule()
    validationRule.event_id = setOf("{enum#__event_id}", "{enum:sessionFinished|searchRestarted}")
    validationRule.enums = mapOf("__event_id" to setOf("enum"))
    validationRule.event_data = mapOf("data" to setOf("{regexp#integer}"))
    validationRule.regexps = mapOf("count" to "\\d+K?M?\\+?")
    eventGroupRemoteDescriptors.rules = validationRule

    val eventGroupRemoteDescriptor = EventGroupRemoteDescriptors.EventGroupRemoteDescriptor()
    eventGroupRemoteDescriptor.rules = validationRule
    eventGroupRemoteDescriptor.id = "id"

    val groupBuildRange = EventGroupRemoteDescriptors.GroupBuildRange()
    groupBuildRange.to = "2"
    groupBuildRange.from = "1"
    eventGroupRemoteDescriptor.builds?.add(groupBuildRange)

    val anonymizedFields = EventGroupRemoteDescriptors.AnonymizedFields()
    anonymizedFields.event = "event"
    anonymizedFields.fields?.add("filed")
    eventGroupRemoteDescriptor.anonymized_fields?.add(anonymizedFields)

    eventGroupRemoteDescriptor.versions?.add(EventGroupRemoteDescriptors.GroupVersionRange("1", "2"))
    eventGroupRemoteDescriptors.groups.add(eventGroupRemoteDescriptor)

    val serializationText = StringWriter(1024)
    SerializationHelper.serialize(serializationText, eventGroupRemoteDescriptors)
    val realText = File(getTestDataRoot() + "SerializationEventGroupRemoteDescriptors.json").readText(Charsets.UTF_8)

    Assertions.assertEquals(realText, serializationText.toString())
  }

  fun testDeserializationEventGroupRemoteDescriptors() {
    val eventGroupRemoteDescriptors = File(getTestDataRoot() + "SerializationEventGroupRemoteDescriptors.json").readText(Charsets.UTF_8)

    val deserializationObject = SerializationHelper.deserialize(eventGroupRemoteDescriptors, EventGroupRemoteDescriptors::class.java)
    val serializationText = SerializationHelper.serialize(deserializationObject)

    Assertions.assertEquals(eventGroupRemoteDescriptors, serializationText)
  }

  fun testCustomSerializationLogEvent() {
    val logEvent = LogEvent("session", "build", "bucket", 1L, LogEventGroup("id", "version"),
                            "recorderVersion", LogEventAction("id", true, mutableMapOf("string" to "any"), 2))
    val serializationText = LogEventSerializer.toString(logEvent)
    val realText = File(getTestDataRoot() + "CustomSerializationLogEvent.json").readText(Charsets.UTF_8)

    Assertions.assertEquals(serializationText, realText)
  }

  fun testCustomDeserializationLogEvent() {
    val logEvent = File(getTestDataRoot() + "CustomSerializationLogEvent.json").readText(Charsets.UTF_8)

    val deserializationObject = com.intellij.internal.statistic.eventLog.SerializationHelper.deserializeLogEvent(logEvent)
    val serializationText = LogEventSerializer.toString(deserializationObject)

    Assertions.assertEquals(logEvent, serializationText)
  }

  fun testSerializationLogEventRecordRequest() {
    val logEvent = LogEvent("session", "build", "bucket", 1L, LogEventGroup("id", "version"),
                            "recorderVersion", LogEventAction("id", true, mutableMapOf("string" to "any"), 2))
    val logEventRecord = LogEventRecord(listOf(logEvent))
    val logEventRecordRequest = LogEventRecordRequest("recorder", "product", "device", listOf(logEventRecord), true)

    val serializationText = SerializationHelper.serialize(logEventRecordRequest)
    val realText = File(getTestDataRoot() + "SerializationLogEventRecordRequest.json").readText(Charsets.UTF_8)

    Assertions.assertEquals(serializationText, realText)
  }

  fun testDeserializationLogEventRecordRequest() {
    val logEventRecordRequest = File(getTestDataRoot() + "SerializationLogEventRecordRequest.json").readText(Charsets.UTF_8)

    val deserializationObject = SerializationHelper.deserialize(logEventRecordRequest, LogEventRecordRequest::class.java)
    val serializationText = SerializationHelper.serialize(deserializationObject)

    Assertions.assertEquals(logEventRecordRequest, serializationText)
  }

  fun testSerializationEventsSchemePrimitive() {
    val filedDescriptor = FieldDescriptor("plugin", setOf("{util#class_name}", "{util#plugin}"))
    val eventSchemeDescriptor = EventDescriptor("testEvent", setOf(filedDescriptor, filedDescriptor))
    val groupDescriptor = GroupDescriptor("testId", "counter", 1, setOf(eventSchemeDescriptor, eventSchemeDescriptor),
                                          "classNameTest", "recorderTest", PluginSchemeDescriptor("pluginIdTest"))
    val eventsScheme = EventsScheme("commitHash", "buildNumber", listOf(groupDescriptor, groupDescriptor))

    val serializationText = SerializationHelper.serialize(eventsScheme)
    val realText = File(getTestDataRoot() + "SerializationEventsSchemePrimitive.json").readText(Charsets.UTF_8)

    Assertions.assertEquals(realText, serializationText)
  }

  fun testDeserializationEventsSchemePrimitive() {
    val eventsScheme = File(getTestDataRoot() + "SerializationEventsSchemePrimitive.json").readText(Charsets.UTF_8)

    val deserializationObject = SerializationHelper.deserialize(eventsScheme, EventsScheme::class.java)
    val serializationText = SerializationHelper.serialize(deserializationObject)

    Assertions.assertEquals(eventsScheme, serializationText)
  }

  fun testSerializationEventsSchemeArray() {
    val filedDescriptor = FieldDescriptor("plugin", setOf("{util#class_name}", "{util#plugin}"), dataType = FieldDataType.ARRAY)
    val eventSchemeDescriptor = EventDescriptor("testEvent", setOf(filedDescriptor, filedDescriptor))
    val groupDescriptor = GroupDescriptor("testId", "counter", 1, setOf(eventSchemeDescriptor, eventSchemeDescriptor),
                                          "classNameTest", "recorderTest", PluginSchemeDescriptor("pluginIdTest"))
    val eventsScheme = EventsScheme("commitHash", "buildNumber", listOf(groupDescriptor, groupDescriptor))

    val serializationText = SerializationHelper.serialize(eventsScheme)
    val realText = File(getTestDataRoot() + "SerializationEventsSchemeArray.json").readText(Charsets.UTF_8)

    Assertions.assertEquals(realText, serializationText)
  }

  fun testSerializationAnonymizedField() {
    val anonymizedField = FieldDescriptor("test_id", setOf("{regexp#hash}"), shouldBeAnonymized = true)
    val anonymizedArray = FieldDescriptor("test_id_array", setOf("{regexp#hash}"), shouldBeAnonymized = true, dataType = FieldDataType.ARRAY)
    val eventSchemeDescriptor = EventDescriptor("testEvent", setOf(anonymizedArray, anonymizedField))
    val groupDescriptor = GroupDescriptor("testId", "counter", 1, setOf(eventSchemeDescriptor),
                                          "classNameTest", "recorderTest", PluginSchemeDescriptor("pluginIdTest"))
    val eventsScheme = EventsScheme("commitHash", "buildNumber", listOf(groupDescriptor))

    val serializationText = SerializationHelper.serialize(eventsScheme)
    val realText = File(getTestDataRoot() + "SerializationAnonymizedField.json").readText(Charsets.UTF_8)

    Assertions.assertEquals(realText, serializationText)
  }

  fun testDeserializationEventsSchemeArray() {
    val eventsScheme = File(getTestDataRoot() + "SerializationEventsSchemeArray.json").readText(Charsets.UTF_8)

    val deserializationObject = SerializationHelper.deserialize(eventsScheme, EventsScheme::class.java)
    val serializationText = SerializationHelper.serialize(deserializationObject)

    Assertions.assertEquals(eventsScheme, serializationText)
  }
}