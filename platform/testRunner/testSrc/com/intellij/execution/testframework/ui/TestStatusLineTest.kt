// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.ui

import com.intellij.ide.nls.NlsMessages
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightPlatformTestCase

class TestStatusLineTest : LightPlatformTestCase() {
  private fun testStatus(
    testsTotal: Int,
    finishedTestsCount: Int,
    failuresCount: Int,
    ignoredTestsCount: Int,
    duration: Long?,
    endTime: Long,
    expectedStatus: String?,
    expectedDescription: String? = null,
  ) {
    val testStatusLine = TestStatusLine()
    ApplicationManager.getApplication().invokeAndWait {
      testStatusLine.formatTestMessage(testsTotal, finishedTestsCount, failuresCount, ignoredTestsCount, duration, endTime)
    }
    expectedStatus?.let { assertEquals(it, testStatusLine.myState.toString()) }
    expectedDescription?.let { assertEquals(it, testStatusLine.stateDescription.toString()) }
  }

  fun testFinished() {
    val duration = 1L
    val durationText = NlsMessages.formatDurationApproximateNarrow(duration)

    testStatus(1, 1, 0, 0, 1L, 1L, "1 test passed", "1 test total, $durationText")
    testStatus(2, 2, 0, 0, 1L, 1L, "2 tests passed", "2 tests total, $durationText")
    testStatus(1, 1, 1, 0, 1L, 1L, "1 test failed", "1 test total, $durationText")
    testStatus(2, 2, 2, 0, 1L, 1L, "2 tests failed", "2 tests total, $durationText")
    testStatus(1, 1, 0, 1, 1L, 1L, "1 test ignored", "1 test total, $durationText")
    testStatus(2, 2, 0, 2, 1L, 1L, "2 tests ignored", "2 tests total, $durationText")
  }

  fun testFinishedMixed() {
    val duration = 1L
    val durationText = NlsMessages.formatDurationApproximateNarrow(duration)

    testStatus(3, 3, 1, 0, 1L, 1L, "1 test failed, 2 passed", "3 tests total, $durationText")
    testStatus(3, 3, 2, 0, 1L, 1L, "2 tests failed, 1 passed", "3 tests total, $durationText")
    testStatus(3, 3, 1, 2, 1L, 1L, "1 test failed, 2 ignored", "3 tests total, $durationText")
    testStatus(3, 3, 2, 1, 1L, 1L, "2 tests failed, 1 ignored", "3 tests total, $durationText")
    testStatus(3, 3, 0, 2, 1L, 1L, "1 test passed, 2 ignored", "3 tests total, $durationText")
    testStatus(3, 3, 0, 1, 1L, 1L, "2 tests passed, 1 ignored", "3 tests total, $durationText")
    testStatus(3, 3, 1, 1, 1L, 1L, "1 test failed, 1 passed, 1 ignored", "3 tests total, $durationText")
    testStatus(4, 4, 2, 1, 1L, 1L, "2 tests failed, 1 passed, 1 ignored", "4 tests total, $durationText")
  }

  fun testInProgress() {
    testStatus(9, 1, 0, 0, null, 0L, "1 test passed", "1 / 9 tests")
    testStatus(9, 2, 0, 0, null, 0L, "2 tests passed", "2 / 9 tests")
    testStatus(9, 1, 1, 0, null, 0L, "1 test failed", "1 / 9 tests")
    testStatus(9, 2, 2, 0, null, 0L, "2 tests failed", "2 / 9 tests")
    testStatus(9, 1, 0, 1, null, 0L, "1 test ignored", "1 / 9 tests")
    testStatus(9, 2, 0, 2, null, 0L, "2 tests ignored", "2 / 9 tests")
  }

  fun testInProgressMixed() {
    testStatus(9, 3, 1, 0, null, 0L, "1 test failed, 2 passed", "3 / 9 tests")
    testStatus(9, 3, 2, 0, null, 0L, "2 tests failed, 1 passed", "3 / 9 tests")
    testStatus(9, 3, 1, 2, null, 0L, "1 test failed, 2 ignored", "3 / 9 tests")
    testStatus(9, 3, 2, 1, null, 0L, "2 tests failed, 1 ignored", "3 / 9 tests")
    testStatus(9, 3, 0, 2, null, 0L, "1 test passed, 2 ignored", "3 / 9 tests")
    testStatus(9, 3, 0, 1, null, 0L, "2 tests passed, 1 ignored", "3 / 9 tests")
    testStatus(9, 3, 1, 1, null, 0L, "1 test failed, 1 passed, 1 ignored", "3 / 9 tests")
    testStatus(9, 4, 2, 1, null, 0L, "2 tests failed, 1 passed, 1 ignored", "4 / 9 tests")
  }

  fun testStopped() {
    val duration = 1L
    val durationText = NlsMessages.formatDurationApproximateNarrow(duration)

    testStatus(9, 1, 0, 0, 1L, 1L, "Stopped. 1 test passed", "1 / 9 tests, $durationText")
    testStatus(9, 2, 0, 0, 1L, 1L, "Stopped. 2 tests passed", "2 / 9 tests, $durationText")
    testStatus(9, 1, 1, 0, 1L, 1L, "Stopped. 1 test failed", "1 / 9 tests, $durationText")
    testStatus(9, 2, 2, 0, 1L, 1L, "Stopped. 2 tests failed", "2 / 9 tests, $durationText")
    testStatus(9, 1, 0, 1, 1L, 1L, "Stopped. 1 test ignored", "1 / 9 tests, $durationText")
    testStatus(9, 2, 0, 2, 1L, 1L, "Stopped. 2 tests ignored", "2 / 9 tests, $durationText")

    testStatus(9, 3, 1, 0, 1L, 1L, "Stopped. 1 test failed, 2 passed", "3 / 9 tests, $durationText")
    testStatus(9, 3, 2, 0, 1L, 1L, "Stopped. 2 tests failed, 1 passed", "3 / 9 tests, $durationText")
    testStatus(9, 3, 1, 2, 1L, 1L, "Stopped. 1 test failed, 2 ignored", "3 / 9 tests, $durationText")
    testStatus(9, 3, 2, 1, 1L, 1L, "Stopped. 2 tests failed, 1 ignored", "3 / 9 tests, $durationText")
    testStatus(9, 3, 0, 2, 1L, 1L, "Stopped. 1 test passed, 2 ignored", "3 / 9 tests, $durationText")
    testStatus(9, 3, 0, 1, 1L, 1L, "Stopped. 2 tests passed, 1 ignored", "3 / 9 tests, $durationText")
    testStatus(9, 3, 1, 1, 1L, 1L, "Stopped. 1 test failed, 1 passed, 1 ignored", "3 / 9 tests, $durationText")
    testStatus(9, 4, 2, 1, 1L, 1L, "Stopped. 2 tests failed, 1 passed, 1 ignored", "4 / 9 tests, $durationText")
  }

  fun testOutdatedTestCount() {
    testStatus(9, 1, 2, 0, null, 0L, "2 tests failed", "2 / 9 tests")
    testStatus(9, 1, 0, 2, null, 0L, "2 tests ignored", "2 / 9 tests")
    testStatus(9, 1, 1, 1, null, 0L, "1 test failed, 1 ignored", "2 / 9 tests")
  }
}