// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.actions.localWhitelist

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.psi.PsiFile
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ValidationRulesCompletionContributorTest : BasePlatformTestCase() {
  fun testPrefixCompletion() {
    val file = myFixture.configureByText("event-log-validation-rules.json", """
      {
        "event_id": ["eventId"],
        "event_data": {
          "foo": ["<caret>"]
        }
      }
    """.trimIndent())
    markValidationRulesFile(file)
    myFixture.completeBasic()
    val strings = myFixture.lookupElementStrings!!
    UsefulTestCase.assertContainsElements(strings, ValidationRulesCompletionContributor.PREFIXES)
  }

  fun testCompletionInEventId() {
    val file = myFixture.configureByText("event-log-validation-rules.json", """
      {
        "event_id": ["<caret>"]
      }
    """.trimIndent())
    markValidationRulesFile(file)
    myFixture.completeBasic()
    val strings = myFixture.lookupElementStrings!!
    UsefulTestCase.assertContainsElements(strings, ValidationRulesCompletionContributor.PREFIXES)
  }



  fun testPrefixInsert() {
    val file = myFixture.configureByText("event-log-validation-rules.json", """
      {
        "event_id": ["eventId"],
        "event_data": {
          "foo": ["uti<caret>"]
        }
      }
    """.trimIndent())
    markValidationRulesFile(file)
    val variants = myFixture.completeBasic()
    myFixture.lookup.currentItem = variants.find { it.lookupString == "{util#}" }
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    myFixture.checkResult("""
      {
        "event_id": ["eventId"],
        "event_data": {
          "foo": ["{util#<caret>}"]
        }
      }
    """.trimIndent())
  }

  fun testInsertPrefixWithBracket() {
    val file = myFixture.configureByText("event-log-validation-rules.json", """
      {
        "event_id": ["eventId"],
        "event_data": {
          "foo": ["{uti<caret>"]
        }
      }
    """.trimIndent())
    markValidationRulesFile(file)
    val variants = myFixture.completeBasic()
    myFixture.lookup.currentItem = variants.find { it.lookupString == "{util#}" }
    myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    myFixture.checkResult("""
      {
        "event_id": ["eventId"],
        "event_data": {
          "foo": ["{util#<caret>}"]
        }
      }
    """.trimIndent())
  }

  fun testNoCompletionInEventIdPropertyKey() {
    val file = myFixture.configureByText("event-log-validation-rules.json", """
      {
        "<caret>": ["eventId"]
      }
    """.trimIndent())
    markValidationRulesFile(file)
    myFixture.completeBasic()
    val strings = myFixture.lookupElementStrings!!
    UsefulTestCase.assertEmpty(strings)
  }


  fun testNoCompletionInDataFieldPropertyKey() {
    val file = myFixture.configureByText("event-log-validation-rules.json", """
      {
        "event_data": {
          "<caret>": ["rule"]
        }
      }
    """.trimIndent())
    markValidationRulesFile(file)
    myFixture.completeBasic()
    val strings = myFixture.lookupElementStrings!!
    UsefulTestCase.assertEmpty(strings)
  }

  fun testReferenceCompletion() {
    val file = myFixture.configureByText("event-log-validation-rules.json", """
      {
        "event_id": ["eventId"],
        "event_data": {
          "foo": ["{<caret>}"]
        }
      }
    """.trimIndent())
    file.putUserData(LocalWhitelistGroupConfiguration.FUS_WHITELIST_COMMON_RULES_KEY,
                     LocalWhitelistGroupConfiguration.ProductionRules(setOf("version", "integer"), setOf("boolean")))
    markValidationRulesFile(file)
    myFixture.completeBasic()
    val strings = myFixture.lookupElementStrings!!
    UsefulTestCase.assertContainsElements(strings, "{regexp#version}", "{regexp#integer}", "{enum#boolean}", "{util#plugin}")
  }

  private fun markValidationRulesFile(file: PsiFile) {
    file.virtualFile.putUserData(LocalWhitelistJsonSchemaProviderFactory.LOCAL_WHITELIST_VALIDATION_RULES_KEY, true)
  }
}