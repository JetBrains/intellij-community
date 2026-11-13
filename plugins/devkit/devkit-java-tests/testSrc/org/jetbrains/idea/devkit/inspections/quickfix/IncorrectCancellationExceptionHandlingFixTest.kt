// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.incorrectCancellationExceptionHandlingFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/incorrectCeHandlingFix")
@TestApplication
class IncorrectCancellationExceptionHandlingFixTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val cancellation by incorrectCancellationExceptionHandlingFixture(
    projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/incorrectCeHandlingFix")
  private val myFixture get() = cancellation.fixture

  @BeforeEach
  fun setUp() {
    myFixture.addClass("""
        package com.intellij.diagnostic;
        public final class ControlFlowExceptionsKt {
        public static void rethrowControlFlowException(Throwable t) {}
        }
        """.trimIndent())
  }

  @Test
  fun testInsertRethrowHelperInPceCatch(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      DevKitInspectionFixTestBase.doTest(myFixture,
                                         DevKitBundle.message("inspections.incorrect.cancellation.exception.insert.rethrow.helper.fix.name"),
                                         "java", myFixture.getTestName(false))
    }
  }

  @Test
  fun testRemoveLoggingStatement(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      DevKitInspectionFixTestBase.doTest(myFixture, DevKitBundle.message("inspections.incorrect.cancellation.exception.remove.logging.fix.name"),
                                         "java", myFixture.getTestName(false))
    }
  }

  @Test
  fun testInsertRethrowHelperInBroadCatch(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      DevKitInspectionFixTestBase.doTest(myFixture,
                                         DevKitBundle.message("inspections.incorrect.cancellation.exception.insert.rethrow.helper.fix.name"),
                                         "java", myFixture.getTestName(false))
    }
  }

  @Test
  fun testInsertRethrowHelperBeforeLogInBroadCatch(): Unit = timeoutRunBlocking {
    val testName = myFixture.getTestName(false)
    myFixture.testHighlighting("$testName.java")
    withContext(Dispatchers.EDT) {
      assertThat(myFixture.filterAvailableIntentions(
        DevKitBundle.message("inspections.incorrect.cancellation.exception.remove.logging.fix.name"))).isEmpty()
      myFixture.launchAction(myFixture.findSingleIntention(
        DevKitBundle.message("inspections.incorrect.cancellation.exception.insert.rethrow.helper.fix.name")))
      myFixture.checkResultByFile("${testName}_after.java")
    }
  }

  // The multi-catch also carries an 'IllegalArgumentException', whose log must stay.
  @Test
  fun testInsertRethrowHelperBeforeLogInMultiCatch(): Unit = timeoutRunBlocking {
    val testName = myFixture.getTestName(false)
    myFixture.testHighlighting("$testName.java")
    withContext(Dispatchers.EDT) {
      assertThat(myFixture.filterAvailableIntentions(
        DevKitBundle.message("inspections.incorrect.cancellation.exception.remove.logging.fix.name"))).isEmpty()
      myFixture.launchAction(myFixture.findSingleIntention(
        DevKitBundle.message("inspections.incorrect.cancellation.exception.insert.rethrow.helper.fix.name")))
      myFixture.checkResultByFile("${testName}_after.java")
    }
  }
}
