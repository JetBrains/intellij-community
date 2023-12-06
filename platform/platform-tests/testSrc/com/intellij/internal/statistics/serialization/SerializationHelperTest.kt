// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.serialization

import com.intellij.internal.statistic.config.EventLogExternalSettings
import com.intellij.internal.statistic.config.SerializationHelper
import com.intellij.internal.statistic.config.bean.EventLogConfigVersions
import com.intellij.internal.statistic.config.bean.EventLogMajorVersionBorders
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
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.File
import java.io.StringReader
import java.io.StringWriter

@Suppress("JUnitMixedFramework")
internal class SerializationHelperTest : BasePlatformTestCase() {
  private fun getTestDataRoot() = PlatformTestUtil.getPlatformTestDataPath() + "fus/serialization/"

  @org.junit.Test
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

  @org.junit.Test
  fun testDeserializationGroupRemoteRule() {
    val validationRule = File(getTestDataRoot() + "SerializationGroupRemoteRule.json").readText(Charsets.UTF_8)

    val deserializationObject = SerializationHelper.deserialize(validationRule, EventGroupRemoteDescriptors.GroupRemoteRule::class.java)
    val serializationText = SerializationHelper.serialize(deserializationObject)

    Assertions.assertEquals(validationRule, serializationText)
  }

  @org.junit.Test
  fun testSerializationGroupDescriptor() {
    val filedDescriptor = FieldDescriptor("plugin", setOf("{util#class_name}", "{util#plugin}"))
    val eventSchemeDescriptor = EventDescriptor("testEvent", setOf(filedDescriptor, filedDescriptor))
    val groupDescriptor = GroupDescriptor("testId", "counter", 1, setOf(eventSchemeDescriptor, eventSchemeDescriptor),
                                          "classNameTest", "recorderTest", PluginSchemeDescriptor("pluginIdTest"))
    val serializationText = SerializationHelper.serialize(groupDescriptor)
    val realText = File(getTestDataRoot() + "SerializationGroupDescriptor.json").readText(Charsets.UTF_8)

    Assertions.assertEquals(realText, serializationText)
  }

  @org.junit.Test
  fun testDeserializationGroupDescriptor() {
    val groupDescriptor = File(getTestDataRoot() + "SerializationGroupDescriptor.json").readText(Charsets.UTF_8)

    val deserializationObject = SerializationHelper.deserialize(groupDescriptor, GroupDescriptor::class.java)
    val serializationText = SerializationHelper.serialize(deserializationObject)

    Assertions.assertEquals(groupDescriptor, serializationText)
  }

  @org.junit.Test
  fun testSerializationFeatureUsageData() {
    val data = FeatureUsageData("FUS")
    data.addData("durationMs", 1)
    data.addData("version", "unknown")
    data.addData("file_path", "testData/Serialization.json")

    val serializationText = SerializationHelper.serializeToSingleLine(data.build())
    val realText = File(getTestDataRoot() + "SerializationFeatureUsageData.json").readText(Charsets.UTF_8)

    Assertions.assertEquals(realText, serializationText)
  }

  @Test
  fun testSerializationEventLogExternalSettings() {
    val eventLogMajorVersionBorders = EventLogMajorVersionBorders()
    eventLogMajorVersionBorders.from = "2019.2"
    eventLogMajorVersionBorders.to = "2020.3"

    val eventLogConfigFilterCondition = EventLogConfigVersions.EventLogConfigFilterCondition()
    eventLogConfigFilterCondition.releaseType = "ALL"
    eventLogConfigFilterCondition.from = 0
    eventLogConfigFilterCondition.to = 256

    val eventLogConfigVersions = EventLogConfigVersions()
    eventLogConfigVersions.majorBuildVersionBorders = eventLogMajorVersionBorders
    eventLogConfigVersions.endpoints = mapOf("send" to "https://send/endpoint")
    eventLogConfigVersions.options = mapOf("option1" to "value1")
    eventLogConfigVersions.releaseFilters = listOf(eventLogConfigFilterCondition)

    val eventLogExternalSettings = EventLogExternalSettings()
    eventLogExternalSettings.productCode = "IU"
    eventLogExternalSettings.versions = listOf(eventLogConfigVersions)

    val serializationText = SerializationHelper.serializeToSingleLine(eventLogExternalSettings)
    val realText = File(getTestDataRoot() + "SerializationEventLogExternalSettings.json").readText(Charsets.UTF_8)

    Assertions.assertEquals(realText, serializationText)
  }

  @Test
  fun testDeserializationEventLogExternalSettings() {
    val config = File(getTestDataRoot() + "SerializationEventLogExternalSettings.json").readText(Charsets.UTF_8)
    val reader = BufferedReader(StringReader(config))
    val deserializationObject = SerializationHelper.deserialize(reader, EventLogExternalSettings::class.java)
    val serializationText = SerializationHelper.serializeToSingleLine(deserializationObject)

    Assertions.assertEquals(serializationText, config)
  }

  @Test
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

  @Test
  fun testDeserializationEventGroupRemoteDescriptors() {
    val eventGroupRemoteDescriptors = File(getTestDataRoot() + "SerializationEventGroupRemoteDescriptors.json").readText(Charsets.UTF_8)

    val deserializationObject = SerializationHelper.deserialize(eventGroupRemoteDescriptors, EventGroupRemoteDescriptors::class.java)
    val serializationText = SerializationHelper.serialize(deserializationObject)

    Assertions.assertEquals(eventGroupRemoteDescriptors, serializationText)
  }

  @Test
  fun testCustomSerializationLogEvent() {
    val logEvent = LogEvent("session", "build", "bucket", 1L, LogEventGroup("id", "version"),
                            "recorderVersion", LogEventAction("id", true, mutableMapOf("string" to "any"), 2))
    val serializationText = LogEventSerializer.toString(logEvent)
    val realText = File(getTestDataRoot() + "CustomSerializationLogEvent.json").readText(Charsets.UTF_8)

    Assertions.assertEquals(serializationText, realText)
  }

  @Test
  fun testCustomDeserializationLogEvent() {
    val logEvent = File(getTestDataRoot() + "CustomSerializationLogEvent.json").readText(Charsets.UTF_8)

    val deserializationObject = com.intellij.internal.statistic.eventLog.SerializationHelper.deserializeLogEvent(logEvent)
    val serializationText = LogEventSerializer.toString(deserializationObject)

    Assertions.assertEquals(logEvent, serializationText)
  }

  @Test
  fun testSerializationLogEventRecordRequest() {
    val logEvent = LogEvent("session", "build", "bucket", 1L, LogEventGroup("id", "version"),
                            "recorderVersion", LogEventAction("id", true, mutableMapOf("string" to "any"), 2))
    val logEventRecord = LogEventRecord(listOf(logEvent))
    val logEventRecordRequest = LogEventRecordRequest("recorder", "product", "device", listOf(logEventRecord), true)

    val serializationText = SerializationHelper.serialize(logEventRecordRequest)
    val realText = File(getTestDataRoot() + "SerializationLogEventRecordRequest.json").readText(Charsets.UTF_8)

    Assertions.assertEquals(serializationText, realText)
  }

  @Test
  fun testDeserializationLogEventRecordRequest() {
    val logEventRecordRequest = File(getTestDataRoot() + "SerializationLogEventRecordRequest.json").readText(Charsets.UTF_8)

    val deserializationObject = SerializationHelper.deserialize(logEventRecordRequest, LogEventRecordRequest::class.java)
    val serializationText = SerializationHelper.serialize(deserializationObject)

    Assertions.assertEquals(logEventRecordRequest, serializationText)
  }

  @Test
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

  @Test
  fun testDeserializationEventsSchemePrimitive() {
    val eventsScheme = File(getTestDataRoot() + "SerializationEventsSchemePrimitive.json").readText(Charsets.UTF_8)

    val deserializationObject = SerializationHelper.deserialize(eventsScheme, EventsScheme::class.java)
    val serializationText = SerializationHelper.serialize(deserializationObject)

    Assertions.assertEquals(eventsScheme, serializationText)
  }

  @Test
  fun testSerializationEventsSchemeArray() {
    val filedDescriptor = FieldDescriptor("plugin", setOf("{util#class_name}", "{util#plugin}"), FieldDataType.ARRAY)
    val eventSchemeDescriptor = EventDescriptor("testEvent", setOf(filedDescriptor, filedDescriptor))
    val groupDescriptor = GroupDescriptor("testId", "counter", 1, setOf(eventSchemeDescriptor, eventSchemeDescriptor),
                                          "classNameTest", "recorderTest", PluginSchemeDescriptor("pluginIdTest"))
    val eventsScheme = EventsScheme("commitHash", "buildNumber", listOf(groupDescriptor, groupDescriptor))

    val serializationText = SerializationHelper.serialize(eventsScheme)
    val realText = File(getTestDataRoot() + "SerializationEventsSchemeArray.json").readText(Charsets.UTF_8)

    Assertions.assertEquals(realText, serializationText)
  }

  @Test
  fun testDeserializationEventsSchemeArray() {
    val eventsScheme = File(getTestDataRoot() + "SerializationEventsSchemeArray.json").readText(Charsets.UTF_8)

    val deserializationObject = SerializationHelper.deserialize(eventsScheme, EventsScheme::class.java)
    val serializationText = SerializationHelper.serialize(deserializationObject)

    Assertions.assertEquals(eventsScheme, serializationText)
  }
}