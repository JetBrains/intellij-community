// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/incorrectCeHandling")
@TestApplication
class IncorrectCancellationExceptionHandlingInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val cancellation by incorrectCancellationExceptionHandlingFixture(
    projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/incorrectCeHandling")
  private val myFixture get() = cancellation.fixture

  @BeforeEach
  fun setUp() {
    myFixture.addClass("""
        package com.example;
        import com.intellij.openapi.diagnostic.ControlFlowException;
        public class SomeControlFlowException extends RuntimeException implements ControlFlowException {}
        """.trimIndent())
    myFixture.addClass("""
        package com.example;
        import com.intellij.openapi.progress.ProcessCanceledException;
        public class SubclassOfProcessCanceledException extends ProcessCanceledException {}
        """.trimIndent())
  }

  @Test
  fun testIncorrectCeLoggedTests() {
    cancellation.doTest()
  }

  @Test
  fun testIncorrectPceHandlingTests() {
    cancellation.doTest()
  }

  @Test
  fun testIncorrectPceHandlingWhenMultipleCatchClausesTests() {
    cancellation.doTest()
  }

  @Test
  fun testIncorrectPceHandlingWhenPceCaughtImplicitlyTests() {
    cancellation.doTest()
  }

  // This fixture has no 'com.intellij.diagnostic.ControlFlowExceptionsKt', as a plugin built against a platform before 263.
  @Test
  fun testInsertRethrowHelperNotOfferedWithoutHelper() {
    myFixture.configureByText("WithoutHelper.java", """
      import com.intellij.openapi.progress.ProcessCanceledException;

      class WithoutHelper {
        void m() {
          try {
            // anything
          } catch (ProcessCanceledException <caret><error descr="'com.intellij.openapi.progress.ProcessCanceledException' must be rethrown">e</error>) {
          }
        }
      }
      """.trimIndent())
    myFixture.checkHighlighting()
    assertThat(myFixture.filterAvailableIntentions(
      DevKitBundle.message("inspections.incorrect.cancellation.exception.insert.rethrow.helper.fix.name"))).isEmpty()
  }

}
