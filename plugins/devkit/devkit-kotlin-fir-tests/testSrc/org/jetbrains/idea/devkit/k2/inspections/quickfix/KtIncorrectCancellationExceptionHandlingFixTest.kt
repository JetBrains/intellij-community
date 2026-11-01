// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.k2.inspections.KtIncorrectCancellationExceptionHandlingInspectionTestBase

@TestDataPath("\$CONTENT_ROOT/testData/inspections/incorrectCeHandlingFix")
class KtIncorrectCancellationExceptionHandlingFixTest : KtIncorrectCancellationExceptionHandlingInspectionTestBase() {

  override fun getFileExtension(): String {
    return "kt"
  }

  override fun getBasePath(): String {
    return super.getBasePath().removeSuffix("incorrectCeHandling") + "incorrectCeHandlingFix"
  }

  override fun setUp() {
    super.setUp()
    addKotlinFile("controlFlowExceptions.kt", """
        package com.intellij.diagnostic
        fun rethrowControlFlowException(e: Throwable?) {}
      """.trimIndent())
  }

  fun testInsertRethrowHelperInPceCatch() {
    doFixTest(DevKitBundle.message("inspections.incorrect.cancellation.exception.insert.rethrow.helper.fix.name"))
  }

  fun testRemoveLoggingStatement() {
    doFixTest(DevKitBundle.message("inspections.incorrect.cancellation.exception.remove.logging.fix.name"))
  }

  fun testInsertRethrowHelperInBroadCatch() {
    doFixTest(DevKitBundle.message("inspections.incorrect.cancellation.exception.insert.rethrow.helper.fix.name"))
  }

  fun testInsertRethrowHelperBeforeLogInBroadCatch() {
    myFixture.testHighlighting(getTestName(false) + ".kt")
    assertEmpty(myFixture.filterAvailableIntentions(
      DevKitBundle.message("inspections.incorrect.cancellation.exception.remove.logging.fix.name")))
    myFixture.launchAction(myFixture.findSingleIntention(
      DevKitBundle.message("inspections.incorrect.cancellation.exception.insert.rethrow.helper.fix.name")))
    myFixture.checkResultByFile(getTestName(false) + "_after.kt")
  }

  private fun doFixTest(fixName: String) {
    myFixture.testHighlighting(getTestName(false) + ".kt")
    myFixture.launchAction(myFixture.findSingleIntention(fixName))
    myFixture.checkResultByFile(getTestName(false) + "_after.kt")
  }
}
