// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.actions.devkit.scheme

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.application.EDT
import com.intellij.platform.statistics.devkit.actions.scheme.EVENTS_TEST_SCHEME_VALIDATION_RULES_KEY
import com.intellij.platform.statistics.devkit.actions.scheme.EventsTestSchemeGroupConfiguration
import com.intellij.platform.statistics.devkit.actions.scheme.PREFIXES
import com.intellij.platform.statistics.devkit.actions.scheme.TOP_LEVEL_PROPERTIES
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertContainsUnordered
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEmpty
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightProjectFixture
import com.intellij.psi.PsiFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

@TestApplication
class ValidationRulesCompletionContributorTest {
  private val projectFixture = codeInsightProjectFixture()
  private val myFixture by codeInsightFixture(projectFixture)

  @Test
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
    assertContainsUnordered(PREFIXES, strings)
  }

  @Test
  fun testCompletionInEventId() {
    val file = myFixture.configureByText("event-log-validation-rules.json", """
      {
        "event_id": ["<caret>"]
      }
    """.trimIndent())
    markValidationRulesFile(file)
    myFixture.completeBasic()
    val strings = myFixture.lookupElementStrings!!
    assertContainsUnordered(PREFIXES, strings)
  }


  @Test
  fun testPrefixInsert(): Unit = timeoutRunBlocking {
    val file = myFixture.configureByText("event-log-validation-rules.json", """
      {
        "event_id": ["eventId"],
        "event_data": {
          "foo": ["uti<caret>"]
        }
      }
    """.trimIndent())
    markValidationRulesFile(file)
    withContext(Dispatchers.EDT) {
      val variants = myFixture.completeBasic()
      myFixture.lookup.currentItem = variants.find { it.lookupString == "{util#}" }
      myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    }
    myFixture.checkResult("""
      {
        "event_id": ["eventId"],
        "event_data": {
          "foo": ["{util#<caret>}"]
        }
      }
    """.trimIndent())
  }

  @Test
  fun testInsertPrefixWithBracket(): Unit = timeoutRunBlocking {
    val file = myFixture.configureByText("event-log-validation-rules.json", """
      {
        "event_id": ["eventId"],
        "event_data": {
          "foo": ["{uti<caret>"]
        }
      }
    """.trimIndent())
    markValidationRulesFile(file)
    withContext(Dispatchers.EDT) {
      val variants = myFixture.completeBasic()
      myFixture.lookup.currentItem = variants.find { it.lookupString == "{util#}" }
      myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    }
    myFixture.checkResult("""
      {
        "event_id": ["eventId"],
        "event_data": {
          "foo": ["{util#<caret>}"]
        }
      }
    """.trimIndent())
  }

  @Test
  fun testTopLevelPropertyCompletion() {
    val file = myFixture.configureByText("event-log-validation-rules.json", """
      {
        "<caret>": ["eventId"]
      }
    """.trimIndent())
    markValidationRulesFile(file)
    myFixture.completeBasic()
    val strings = myFixture.lookupElementStrings!!
    assertContainsUnordered(TOP_LEVEL_PROPERTIES, strings)
  }


  @Test
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
    assertEmpty(strings)
  }

  @Test
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
    assertContainsUnordered(listOf("{regexp#version}", "{regexp#integer}", "{enum#boolean}", "{util#plugin}"), strings)
  }

  private fun markValidationRulesFile(file: PsiFile) {
    file.virtualFile.putUserData(EVENTS_TEST_SCHEME_VALIDATION_RULES_KEY, true)
  }
}
