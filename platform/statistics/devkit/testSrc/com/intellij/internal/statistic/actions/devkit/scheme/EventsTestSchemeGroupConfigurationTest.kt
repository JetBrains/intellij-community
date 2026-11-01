// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.actions.devkit.scheme

import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.eventLog.events.scheme.EventDescriptor
import com.intellij.internal.statistic.eventLog.events.scheme.FieldDescriptor
import com.intellij.internal.statistic.eventLog.events.scheme.GroupDescriptor
import com.intellij.internal.statistic.eventLog.events.scheme.PluginSchemeDescriptor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.statistics.devkit.actions.scheme.EventsTestSchemeGroupConfiguration
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.fus.reporting.model.metadata.EventGroupRemoteDescriptors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class EventsTestSchemeGroupConfigurationTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project get() = projectFixture.get()

  @BeforeEach
  fun setUp() {
    System.setProperty("fus.internal.test.mode", "true")
  }

  @Test
  fun testEventsSchemeSeparators() {
    val fieldDescriptor = FieldDescriptor("plugin", setOf("{util#class_name}", "{util#plugin}"))
    val eventDescriptor = EventDescriptor("testEvent", setOf(fieldDescriptor))
    val groupDescriptor = GroupDescriptor("testId", "counter", 1, setOf(eventDescriptor),
                                          "classNameTest", "recorderTest", PluginSchemeDescriptor("pluginIdTest"))

    val scheme = EventsTestSchemeGroupConfiguration.createEventsScheme(listOf(groupDescriptor))

    scheme["testId"]?.let { StringUtil.assertValidSeparators(it) }
  }

  @Test
  fun testNotValidJson() {
    val rules = """
      {
        "event_id": [,
        "event_data": {
          "data": ["testRule"]       
      }
    """.trimIndent()
    val validationInfo = validateCustomValidationRules(rules)
    assertEquals(1, validationInfo.size) { validationInfo.toString() }
    assertTrue(validationInfo.first().message.contains(StatisticsBundle.message("stats.unable.to.parse.validation.rules")),
               "Validation info should contains incorrect eventId")
  }

  @Test
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

    val validationInfo = validateCustomValidationRules(rules)
    CollectionAssertions.assertEmpty(validationInfo) {
      "Validation errors were found: ${validationInfo.joinToString { "\"${it.message}\"" }}"
    }
  }

  @Test
  fun testValidateEventData() {
    val rules = """
      {
        "event_id": ["testEvent"],
        "event_data": {
          "data_2": []
        }
      }
    """.trimIndent()
    val validationInfo = validateCustomValidationRules(rules)
    assertEquals(1, validationInfo.size) { validationInfo.toString() }
    assertTrue(validationInfo.first().message.contains("Line 4"), "Validation info should contains error")
  }

  @Test
  fun testValidateEventId() {
    val rules = """
      {
        "event_id": [],
        "event_data": {
          "data": ["testRule"]
        }
      }
    """.trimIndent()
    val validationInfo = validateCustomValidationRules(rules)
    assertEquals(1, validationInfo.size) { validationInfo.toString() }
    assertTrue(validationInfo.first().message.contains("Line 2"), "Validation info should contains incorrect eventId")
  }

  @Test
  fun testValidateMissingEventId() {
    val rules = """
      {
        "event_data": {}
      }
    """.trimIndent()
    val validationInfo = validateCustomValidationRules(rules)
    assertEquals(1, validationInfo.size) { validationInfo.toString() }
    assertTrue(validationInfo.first().message.contains("event_id"))
  }

  @Test
  fun testValidateEventDataRuleType() {
    val rules = """
      {
        "event_id": ["testEvent"],
        "event_data": {
          "data": [1]
        }
      }
    """.trimIndent()
    val validationInfo = validateCustomValidationRules(rules)
    assertEquals(1, validationInfo.size) { validationInfo.toString() }
    assertTrue(validationInfo.first().message.contains("Line 4"))
  }

  @Test
  fun testValidateEventDataObject() {
    val rules = """
      {
        "event_id": ["testEvent"],
        "event_data": []
      }
    """.trimIndent()
    val validationInfo = validateCustomValidationRules(rules)
    assertEquals(1, validationInfo.size) { validationInfo.toString() }
    assertTrue(validationInfo.first().message.contains("Line 3"))
  }

  @Test
  fun testEventsSchemeIncludesDefaultValueRule() {
    val fieldDescriptor = FieldDescriptor("unsafe_id", setOf("{enum:}", "{default_value:Unsafe plugin}"))
    val eventDescriptor = EventDescriptor("testEvent", setOf(fieldDescriptor))
    val groupDescriptor = GroupDescriptor("testId", "counter", 1, setOf(eventDescriptor),
                                          "classNameTest", "recorderTest", PluginSchemeDescriptor("pluginIdTest"))

    val scheme = EventsTestSchemeGroupConfiguration.createEventsScheme(listOf(groupDescriptor))

    assertTrue(scheme["testId"]!!.contains("{default_value:Unsafe plugin|testEvent|1}"),
               "Generated scheme should contain {default_value:Unsafe plugin|testEvent|1}")
  }

  @Test
  fun testEventsSchemeIncludesRequiredRule() {
    val fieldDescriptor = FieldDescriptor("my_field", setOf("{enum:a|b}", "{required:true}"))
    val eventDescriptor = EventDescriptor("testEvent", setOf(fieldDescriptor))
    val groupDescriptor = GroupDescriptor("testId", "counter", 1, setOf(eventDescriptor),
                                          "classNameTest", "recorderTest", PluginSchemeDescriptor("pluginIdTest"))

    val scheme = EventsTestSchemeGroupConfiguration.createEventsScheme(listOf(groupDescriptor))

    assertTrue(scheme["testId"]!!.contains("{required:true|testEvent|1}"),
               "Generated scheme should contain {required:true|testEvent|1}")
  }

  @Test
  fun testDefaultValueRuleMultipleEvents() {
    val fieldDescriptor = FieldDescriptor("shared_field", setOf("{enum:a|b}", "{default_value:fallback}"))
    val eventDescriptor1 = EventDescriptor("event.one", setOf(fieldDescriptor))
    val eventDescriptor2 = EventDescriptor("event.two", setOf(fieldDescriptor))
    val groupDescriptor = GroupDescriptor("testId", "counter", 1, setOf(eventDescriptor1, eventDescriptor2),
                                          "classNameTest", "recorderTest", PluginSchemeDescriptor("pluginIdTest"))

    val scheme = EventsTestSchemeGroupConfiguration.createEventsScheme(listOf(groupDescriptor))

    val schemeContent = scheme["testId"]!!
    assertTrue(schemeContent.contains("{default_value:fallback|event.one|1}"),
               "Generated scheme should contain {default_value:fallback|event.one|1}")
    assertTrue(schemeContent.contains("{default_value:fallback|event.two|1}"),
               "Generated scheme should contain {default_value:fallback|event.two|1}")
  }

  private fun validateCustomValidationRules(rules: String): List<ValidationInfo> = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      EventsTestSchemeGroupConfiguration.validateCustomValidationRules(project, rules, null)
    }
  }
}
