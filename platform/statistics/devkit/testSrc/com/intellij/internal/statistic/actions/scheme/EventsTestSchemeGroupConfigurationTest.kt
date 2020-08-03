// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions.scheme

import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import org.intellij.lang.annotations.Language

class EventsTestSchemeGroupConfigurationTest : BasePlatformTestCase() {

  override fun setUp() {
    super.setUp()
    System.setProperty("fus.internal.test.mode", "true")
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
    val globalRules = FUStatisticsWhiteListGroupsService.WLRule()
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
    UsefulTestCase.assertSize(1, validationInfo)
    TestCase.assertTrue("Validation info should contains error",
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
    UsefulTestCase.assertSize(1, validationInfo)
    TestCase.assertTrue("Validation info should contains incorrect eventId",
                        validationInfo.first().message.contains("Line 2"))
  }

}