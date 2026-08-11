// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.actions.devkit.scheme

import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.platform.statistics.devkit.actions.scheme.EventsTestSchemeGroupConfiguration
import com.intellij.internal.statistic.eventLog.events.scheme.EventDescriptor
import com.intellij.internal.statistic.eventLog.events.scheme.FieldDescriptor
import com.intellij.internal.statistic.eventLog.events.scheme.GroupDescriptor
import com.intellij.internal.statistic.eventLog.events.scheme.PluginSchemeDescriptor
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.fus.reporting.model.metadata.EventGroupRemoteDescriptors
import org.intellij.lang.annotations.Language

class EventsTestSchemeGroupConfigurationTest : BasePlatformTestCase() {

  override fun setUp() {
    super.setUp()
    System.setProperty("fus.internal.test.mode", "true")
  }

  fun testEventsSchemeSeparators() {
    val fieldDescriptor = FieldDescriptor("plugin", setOf("{util#class_name}", "{util#plugin}"))
    val eventDescriptor = EventDescriptor("testEvent", setOf(fieldDescriptor))
    val groupDescriptor = GroupDescriptor("testId", "counter", 1, setOf(eventDescriptor),
                                          "classNameTest", "recorderTest", PluginSchemeDescriptor("pluginIdTest"))

    val scheme = EventsTestSchemeGroupConfiguration.createEventsScheme(listOf(groupDescriptor))

    scheme["testId"]?.let { StringUtil.assertValidSeparators(it) }
  }

  fun testNotValidJson() {
    val rules = """
      {
        "event_id": [,
        "event_data": {
          "data": ["testRule"]       
      }
    """.trimIndent()
    val validationInfo = EventsTestSchemeGroupConfiguration.validateCustomValidationRules(project, rules, null)
    assertSize(1, validationInfo)
    assertTrue("Validation info should contains incorrect eventId",
                        validationInfo.first().message.contains(StatisticsBundle.message("stats.unable.to.parse.validation.rules")))
  }

  fun testValidation() {
    @Language("JSON")
    val rules = """
      {
        "event_id": [
          "additionalTabs.count.{regexp#integer}"
        ],
        "event_data": {
          "plugin_type": [
            "{util#plugin_type}"
          ],
          "my_rule": [
            "{rule:TRUE}"
          ],
          "my_enum": [
            "{enum:A|B|C}"
          ],
          "my_enum_ref": [
            "{enum#boolean}"
          ],
          "my_utils": [
            "{util#lang}"
          ],
          "my_regexp": [
            "{regexp:\\d+}"
          ],
          "my_regexp_ref": [
            "{regexp#integer}"
          ]
        }
      }
    """.trimIndent()
    val globalRules = EventGroupRemoteDescriptors.GroupRemoteRule()
    globalRules.regexps = mapOf("integer" to "testIntegerRegexp")
    globalRules.enums = mapOf("boolean" to setOf("true", "false"))

    val validationInfo = EventsTestSchemeGroupConfiguration.validateCustomValidationRules(project, rules, null)
    assertEmpty("Validation errors were found: ${validationInfo.joinToString { "\"${it.message}\"" }}", validationInfo)
  }

  fun testValidateEventData() {
    val rules = """
      {
        "event_id": ["testEvent"],
        "event_data": {
          "data_2": []
        }
      }
    """.trimIndent()
    val validationInfo = EventsTestSchemeGroupConfiguration.validateCustomValidationRules(project, rules, null)
    assertSize(1, validationInfo)
    assertTrue("Validation info should contains error",
                        validationInfo.first().message.contains("Line 4"))
  }

  fun testValidateEventId() {
    val rules = """
      {
        "event_id": [],
        "event_data": {
          "data": ["testRule"]
        }
      }
    """.trimIndent()
    val validationInfo = EventsTestSchemeGroupConfiguration.validateCustomValidationRules(project, rules, null)
    assertSize(1, validationInfo)
    assertTrue("Validation info should contains incorrect eventId",
                        validationInfo.first().message.contains("Line 2"))
  }

  fun testEventsSchemeIncludesDefaultValueRule() {
    val fieldDescriptor = FieldDescriptor("unsafe_id", setOf("{enum:}", "{default_value:Unsafe plugin}"))
    val eventDescriptor = EventDescriptor("testEvent", setOf(fieldDescriptor))
    val groupDescriptor = GroupDescriptor("testId", "counter", 1, setOf(eventDescriptor),
                                          "classNameTest", "recorderTest", PluginSchemeDescriptor("pluginIdTest"))

    val scheme = EventsTestSchemeGroupConfiguration.createEventsScheme(listOf(groupDescriptor))

    assertTrue("Generated scheme should contain {default_value:Unsafe plugin|testEvent|1}",
               scheme["testId"]!!.contains("{default_value:Unsafe plugin|testEvent|1}"))
  }

  fun testEventsSchemeIncludesRequiredRule() {
    val fieldDescriptor = FieldDescriptor("my_field", setOf("{enum:a|b}", "{required:true}"))
    val eventDescriptor = EventDescriptor("testEvent", setOf(fieldDescriptor))
    val groupDescriptor = GroupDescriptor("testId", "counter", 1, setOf(eventDescriptor),
                                          "classNameTest", "recorderTest", PluginSchemeDescriptor("pluginIdTest"))

    val scheme = EventsTestSchemeGroupConfiguration.createEventsScheme(listOf(groupDescriptor))

    assertTrue("Generated scheme should contain {required:true|testEvent|1}",
               scheme["testId"]!!.contains("{required:true|testEvent|1}"))
  }

  fun testDefaultValueRuleMultipleEvents() {
    val fieldDescriptor = FieldDescriptor("shared_field", setOf("{enum:a|b}", "{default_value:fallback}"))
    val eventDescriptor1 = EventDescriptor("event.one", setOf(fieldDescriptor))
    val eventDescriptor2 = EventDescriptor("event.two", setOf(fieldDescriptor))
    val groupDescriptor = GroupDescriptor("testId", "counter", 1, setOf(eventDescriptor1, eventDescriptor2),
                                          "classNameTest", "recorderTest", PluginSchemeDescriptor("pluginIdTest"))

    val scheme = EventsTestSchemeGroupConfiguration.createEventsScheme(listOf(groupDescriptor))

    val schemeContent = scheme["testId"]!!
    assertTrue("Generated scheme should contain {default_value:fallback|event.one|1}",
               schemeContent.contains("{default_value:fallback|event.one|1}"))
    assertTrue("Generated scheme should contain {default_value:fallback|event.two|1}",
               schemeContent.contains("{default_value:fallback|event.two|1}"))
  }
}