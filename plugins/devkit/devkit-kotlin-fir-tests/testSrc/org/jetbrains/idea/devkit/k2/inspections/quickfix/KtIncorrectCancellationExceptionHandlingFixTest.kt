// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.quickfix

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.k2.inspections.ktIncorrectCancellationExceptionHandlingFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/incorrectCeHandlingFix")
@TestApplication
class KtIncorrectCancellationExceptionHandlingFixTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val cancellation by ktIncorrectCancellationExceptionHandlingFixture(
    projectFixture, DevkitKtTestsUtil.TESTDATA_PATH + "inspections/incorrectCeHandlingFix")
  private val myFixture get() = cancellation.fixture

  @BeforeEach
  fun setUp() {
    myFixture.addFileToProject("controlFlowExceptions.kt", """
        package com.intellij.diagnostic
        fun rethrowControlFlowException(e: Throwable?) {}
      """.trimIndent())
  }

  @Test
  fun testInsertRethrowHelperInPceCatch(): Unit = timeoutRunBlocking {
    doFixTest(DevKitBundle.message("inspections.incorrect.cancellation.exception.insert.rethrow.helper.fix.name"))
  }

  @Test
  fun testRemoveLoggingStatement(): Unit = timeoutRunBlocking {
    doFixTest(DevKitBundle.message("inspections.incorrect.cancellation.exception.remove.logging.fix.name"))
  }

  @Test
  fun testInsertRethrowHelperInBroadCatch(): Unit = timeoutRunBlocking {
    doFixTest(DevKitBundle.message("inspections.incorrect.cancellation.exception.insert.rethrow.helper.fix.name"))
  }

  @Test
  fun testInsertRethrowHelperBeforeLogInBroadCatch(): Unit = timeoutRunBlocking {
    myFixture.testHighlighting(myFixture.getTestName(false) + ".kt")
    withContext(Dispatchers.EDT) {
      assertThat(myFixture.filterAvailableIntentions(
        DevKitBundle.message("inspections.incorrect.cancellation.exception.remove.logging.fix.name"))).isEmpty()
      myFixture.launchAction(myFixture.findSingleIntention(
        DevKitBundle.message("inspections.incorrect.cancellation.exception.insert.rethrow.helper.fix.name")))
      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.kt")
    }
  }

  private suspend fun doFixTest(fixName: String) {
    myFixture.testHighlighting(myFixture.getTestName(false) + ".kt")
    withContext(Dispatchers.EDT) {
      myFixture.launchAction(myFixture.findSingleIntention(fixName))
      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.kt")
    }
  }
}
