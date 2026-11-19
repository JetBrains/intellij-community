// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.cancellationCheckInLoopsFixture
import org.jetbrains.idea.devkit.inspections.cancellationCheckInLoopsProjectFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/cancellationCheckInLoops")
@TestApplication
class KtCancellationCheckInLoopsInspectionTest {
  companion object {
    private val projectFixture = cancellationCheckInLoopsProjectFixture()
  }

  private val cancellationCheck by cancellationCheckInLoopsFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/cancellationCheckInLoops")
  private val myFixture get() = cancellationCheck.fixture

  @BeforeEach
  fun setUp() {
    myFixture.addFileToProject(
      "Coroutines.kt",
      //language=kotlin
      """
      package com.intellij.openapi.progress

      suspend fun checkCanceled() { }
      """.trimIndent())
  }

  @Test
  fun testRunCondition() {
    cancellationCheck.doTest()
  }

  @Test
  fun testRunConditionOnSuperMethod() {
    cancellationCheck.doTest()
  }

  @Test
  fun testSuspendingContext() {
    cancellationCheck.doTest()
  }

  @Test
  fun testPresentCancellationCheck() {
    cancellationCheck.doTest()
  }

  @Test
  fun testCancellationCheckPresentNotInFirstLine() {
    cancellationCheck.doTest()
  }

  @Test
  fun testForEachLoops() {
    cancellationCheck.doTest()
  }

  @Test
  fun testWhileLoops() {
    cancellationCheck.doTest()
  }

  @Test
  fun testDoWhileLoops() {
    cancellationCheck.doTest()
  }

  @Test
  fun testNestedLoops() {
    cancellationCheck.doTest()
  }

  @Test
  fun testLoopMethods() {
    cancellationCheck.doTest()
  }

  @Test
  fun testNestedLoopMethods() {
    cancellationCheck.doTest()
  }

}
