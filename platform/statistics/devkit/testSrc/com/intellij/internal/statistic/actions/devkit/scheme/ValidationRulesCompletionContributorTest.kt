// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.actions.devkit.scheme

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.internal.statistic.devkit.actions.scheme.EventsSchemeJsonSchemaProviderFactory
import com.intellij.internal.statistic.devkit.actions.scheme.EventsTestSchemeGroupConfiguration
import com.intellij.internal.statistic.devkit.actions.scheme.ValidationRulesCompletionContributor
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
    file.putUserData(EventsTestSchemeGroupConfiguration.FUS_TEST_SCHEME_COMMON_RULES_KEY,
                     EventsTestSchemeGroupConfiguration.ProductionRules(setOf("version", "integer"), setOf("boolean")))
    markValidationRulesFile(file)
    myFixture.completeBasic()
    val strings = myFixture.lookupElementStrings!!
    UsefulTestCase.assertContainsElements(strings, "{regexp#version}", "{regexp#integer}", "{enum#boolean}", "{util#plugin}")
  }

  private fun markValidationRulesFile(file: PsiFile) {
    file.virtualFile.putUserData(EventsSchemeJsonSchemaProviderFactory.EVENTS_TEST_SCHEME_VALIDATION_RULES_KEY, true)
  }
}