// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/incorrectCeHandling")
class IncorrectCancellationExceptionHandlingInspectionTest : IncorrectCancellationExceptionHandlingInspectionTestBase() {

  override fun setUp() {
    super.setUp()
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

  fun testIncorrectCeLoggedTests() {
    doTest()
  }

  fun testIncorrectPceHandlingTests() {
    doTest()
  }

  fun testIncorrectPceHandlingWhenMultipleCatchClausesTests() {
    doTest()
  }

  fun testIncorrectPceHandlingWhenPceCaughtImplicitlyTests() {
    doTest()
  }

  // This fixture has no 'com.intellij.diagnostic.ControlFlowExceptionsKt', as a plugin built against a platform before 263.
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
    assertEmpty(myFixture.filterAvailableIntentions(
      DevKitBundle.message("inspections.incorrect.cancellation.exception.insert.rethrow.helper.fix.name")))
  }

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/incorrectCeHandling"

  override fun getFileExtension() = "java"

}
