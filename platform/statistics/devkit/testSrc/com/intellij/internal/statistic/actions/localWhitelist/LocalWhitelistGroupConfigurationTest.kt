// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions.localWhitelist

import com.intellij.internal.statistic.service.fus.FUStatisticsWhiteListGroupsService
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import org.intellij.lang.annotations.Language

class LocalWhitelistGroupConfigurationTest : BasePlatformTestCase() {
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

    val validationInfo = LocalWhitelistGroupConfiguration.validateEventData("testGroupId", rules, globalRules)
    assertEmpty("Validation errors were found: ${validationInfo.joinToString { "\"${it.message}\"" }}", validationInfo)
  }

  fun testValidateRule() {
    val incorrectValidationRule = "{util#lang"
    val rules = """
      {
        "event_id": [],
        "event_data": {
          "my_regexp_ref": [
            "$incorrectValidationRule"
          ]
        }
      }
    """.trimIndent()
    val validationInfo = LocalWhitelistGroupConfiguration.validateEventData("testGroupId", rules, FUStatisticsWhiteListGroupsService.WLRule())
    UsefulTestCase.assertSize(1, validationInfo)
    TestCase.assertTrue("Validation info should contains incorrect eventId",
                        validationInfo.first().message.contains(incorrectValidationRule))
  }

  fun testValidateEventId() {
    val incorrectEventId = "{enum#not_exist}"
    val rules = """
      {
        "event_id": [
        "$incorrectEventId"
        ],
        "event_data": {
        }
      }
    """.trimIndent()
    val validationInfo = LocalWhitelistGroupConfiguration.validateEventData("testGroupId", rules, FUStatisticsWhiteListGroupsService.WLRule())
    UsefulTestCase.assertSize(1, validationInfo)
    TestCase.assertTrue("Validation info should contains incorrect eventId",
                        validationInfo.first().message.contains(incorrectEventId))
  }

}