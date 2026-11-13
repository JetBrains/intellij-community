// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.completion

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Checks the code completion in [fixture]. It replaces the JUnit 3 bases `GroovyCompletionTestBase` and `GrBuilderTransformationCompletionTestBase`.
 *
 * When a helper gets no text, it configures the file `<test name>.groovy` from the test data.
 * It compares the result with the file `<test name>_after.groovy`.
 * The helpers work on the EDT and off it.
 */
@TestOnly
class GroovyCompletionFixture internal constructor(
  val fixture: LightJavaCodeInsightTestFixture,
) {
  private val testName: String
    get() = fixture.getTestName(false)

  fun doSmartTest() {
    doCompletionTest(CompletionType.SMART)
  }

  fun doBasicTest(before: String?, after: String?) {
    doCompletionTest(before, after, CompletionType.BASIC)
  }

  fun doBasicTest(before: String?) {
    doBasicTest(before, null)
  }

  fun doBasicTest() {
    doBasicTest(null, null)
  }

  fun doSmartTest(before: String?, after: String?) {
    doCompletionTest(before, after, CompletionType.SMART)
  }

  fun checkResult() {
    fixture.checkResultByFile("${testName}_after.groovy", true)
  }

  /** Completes the single item at the caret, types [type], and checks the result. */
  fun doCompletionTest(before: String?, after: String?, type: String, ct: CompletionType) {
    // The completion and the typing run on the EDT, from the start to the last check.
    timeoutRunBlocking { withContext(Dispatchers.EDT) {
      configure(before)
      fixture.complete(ct)
      for (ch in type) {
        fixture.type(ch)
      }
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
      val msg = fixture.lookupElementStrings?.joinToString(", ") ?: "<empty>"
      assertNull(fixture.lookupElements, msg)
      if (after == null) {
        fixture.checkResultByFile("${testName}_after.groovy", true)
      }
      else {
        fixture.checkResult(after, true)
      }
    } }
  }

  fun doCompletionTest(before: String?, after: String?, ct: CompletionType) {
    doCompletionTest(before, after, "", ct)
  }

  fun doCompletionTest(before: String?, ct: CompletionType) {
    doCompletionTest(before, null, "", ct)
  }

  fun doCompletionTest(ct: CompletionType) {
    doCompletionTest(null, null, "", ct)
  }

  /** Checks that the completion at the caret of [text] gives the lookup items [args]. */
  fun doCompletionTest(text: String?, vararg args: String) {
    doCompletionTest(text, CompletionResult.contain, *args)
  }

  fun doCompletionTest(text: String?, cr: CompletionResult, vararg args: String) {
    doVariantableTest(text, "", CompletionType.BASIC, cr, 1, *args)
  }

  /** Invokes the completion at the caret, types [type], and checks the lookup items against [variants] as [testType] tells. */
  fun doVariantableTest(before: String?, type: String, ct: CompletionType, testType: CompletionResult, completionCount: Int, vararg variants: String) {
    // The completion and the typing run on the EDT, from the start to the last check.
    timeoutRunBlocking { withContext(Dispatchers.EDT) {
      configure(before)
      fixture.complete(ct, completionCount)
      for (ch in type) {
        fixture.type(ch)
      }
      assertNotNull(fixture.lookupElements)

      val actual = fixture.lookupElementStrings!!
      val msg = fixture.lookupElementStrings?.joinToString(", ") ?: "<empty>"
      when (testType) {
        CompletionResult.contain -> assertTrue(actual.containsAll(variants.asList()), msg)
        CompletionResult.equal -> {
          if (variants.isEmpty()) {
            assertTrue(actual.isEmpty())
          }
          else {
            fixture.assertPreferredCompletionItems(0, *variants)
          }
        }
        CompletionResult.notContain -> {
          for (variant in variants) {
            assertFalse(actual.contains(variant), msg)
          }
        }
      }
    } }
  }

  fun doVariantableTest(before: String?, type: String, ct: CompletionType, testType: CompletionResult, vararg variants: String) {
    doVariantableTest(before, type, ct, testType, 1, *variants)
  }

  fun doVariantableTest(before: String?, type: String, ct: CompletionType, vararg variants: String) {
    doVariantableTest(before, type, ct, CompletionResult.equal, 1, *variants)
  }

  fun doVariantableTest(before: String?, ct: CompletionType, vararg variants: String) {
    doVariantableTest(before, "", ct, CompletionResult.equal, 1, *variants)
  }

  fun doVariantableTest(ct: CompletionType, vararg variants: String) {
    doVariantableTest(null, "", ct, CompletionResult.equal, 1, *variants)
  }

  fun doVariantableTest(vararg variants: String) {
    doVariantableTest(CompletionType.BASIC, *variants)
  }

  fun doHasVariantsTest(vararg variants: String) {
    doVariantableTest(null, "", CompletionType.BASIC, CompletionResult.contain, *variants)
  }

  fun doSmartCompletion(vararg variants: String) {
    doVariantableTest(CompletionType.SMART, *variants)
  }

  fun checkCompletion(before: String?, type: String, after: String?) {
    doCompletionTest(before, after, type, CompletionType.BASIC)
  }

  fun checkSingleItemCompletion(before: String?, after: String?) {
    doCompletionTest(before, after, CompletionType.BASIC)
  }

  fun doNoVariantsTest(before: String?, vararg excludedVariants: String) {
    doVariantableTest(before, "", CompletionType.BASIC, CompletionResult.notContain, *excludedVariants)
  }

  /** The active lookup of the editor. */
  val lookup: LookupImpl?
    get() = LookupManager.getActiveLookup(fixture.editor) as LookupImpl?

  private fun configure(before: String?) {
    if (before == null) {
      fixture.configureByFile("$testName.groovy")
    }
    else {
      fixture.configureByText("$testName.groovy", before)
    }
  }
}

/**
 * Creates a [GroovyCompletionFixture] in the project of [projectFixture] for each test.
 * The test data files have the name of the test method without the prefix `test`.
 * [testDataPath] is the absolute test data path. By default, it is the community root.
 */
@TestOnly
@JvmOverloads
fun groovyCompletionFixture(projectFixture: TestFixture<LightJavaProject>, testDataPath: String? = null): TestFixture<GroovyCompletionFixture> =
  testFixture("groovyCompletionFixture") {
    val fixture = lightJavaCodeInsightFixture(projectFixture).init()
    if (testDataPath != null) {
      fixture.testDataPath = testDataPath
    }
    initialized(GroovyCompletionFixture(fixture)) {}
  }
