// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.IncorrectCancellationExceptionHandlingInspectionTestBase

@TestDataPath("\$CONTENT_ROOT/testData/inspections/incorrectCeHandlingFix")
class IncorrectCancellationExceptionHandlingFixTest : IncorrectCancellationExceptionHandlingInspectionTestBase() {

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
        package com.intellij.diagnostic;
        public final class ControlFlowExceptionsKt {
          public static void rethrowControlFlowException(Throwable t) {}
        }
        """.trimIndent())
  }

  override fun getFileExtension(): String {
    return "java"
  }

  override fun getBasePath(): String {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/incorrectCeHandlingFix"
  }

  fun testInsertRethrowHelperInPceCatch() {
    DevKitInspectionFixTestBase.doTest(myFixture,
                                       DevKitBundle.message("inspections.incorrect.cancellation.exception.insert.rethrow.helper.fix.name"),
                                       getFileExtension(), getTestName(false))
  }

  fun testRemoveLoggingStatement() {
    DevKitInspectionFixTestBase.doTest(myFixture, DevKitBundle.message("inspections.incorrect.cancellation.exception.remove.logging.fix.name"),
                                       getFileExtension(), getTestName(false))
  }

  fun testInsertRethrowHelperInBroadCatch() {
    DevKitInspectionFixTestBase.doTest(myFixture,
                                       DevKitBundle.message("inspections.incorrect.cancellation.exception.insert.rethrow.helper.fix.name"),
                                       getFileExtension(), getTestName(false))
  }

  fun testInsertRethrowHelperBeforeLogInBroadCatch() {
    val testName = getTestName(false)
    myFixture.testHighlighting("$testName.java")
    assertEmpty(myFixture.filterAvailableIntentions(
      DevKitBundle.message("inspections.incorrect.cancellation.exception.remove.logging.fix.name")))
    myFixture.launchAction(myFixture.findSingleIntention(
      DevKitBundle.message("inspections.incorrect.cancellation.exception.insert.rethrow.helper.fix.name")))
    myFixture.checkResultByFile("${testName}_after.java")
  }

  // The multi-catch also carries an 'IllegalArgumentException', whose log must stay.
  fun testInsertRethrowHelperBeforeLogInMultiCatch() {
    val testName = getTestName(false)
    myFixture.testHighlighting("$testName.java")
    assertEmpty(myFixture.filterAvailableIntentions(
      DevKitBundle.message("inspections.incorrect.cancellation.exception.remove.logging.fix.name")))
    myFixture.launchAction(myFixture.findSingleIntention(
      DevKitBundle.message("inspections.incorrect.cancellation.exception.insert.rethrow.helper.fix.name")))
    myFixture.checkResultByFile("${testName}_after.java")
  }
}
