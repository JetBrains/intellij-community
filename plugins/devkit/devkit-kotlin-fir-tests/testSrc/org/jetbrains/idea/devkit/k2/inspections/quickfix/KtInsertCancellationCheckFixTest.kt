// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.cancellationCheckInLoopsFixture
import org.jetbrains.idea.devkit.inspections.cancellationCheckInLoopsProjectFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/insertCancellationCheckFix")
@TestApplication
class KtInsertCancellationCheckFixTest {
  companion object {
    private val projectFixture = cancellationCheckInLoopsProjectFixture()
  }

  private val cancellationCheck by cancellationCheckInLoopsFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/insertCancellationCheckFix")
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

  private val fixName = DevKitBundle.message("inspection.insert.cancellation.check.fix.message")

  // loops:

  @Test
  fun testBlockDoWhileLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testBlockForEachLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testBlockWhileLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testEmptyDoWhileLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testEmptyForEachLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testEmptyWhileLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testSingleLineDoWhileLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testSingleLineForEachLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testSingleLineWhileLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testNoBodyForEachLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testNoBodyWhileLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testSuspendingDoWhileLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testSuspendingForEachLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testSuspendingLambdaForEachLoop() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testSuspendingWhileLoop() {
    cancellationCheck.doTest(fixName)
  }

  // loop methods:

  @Test
  fun testBlockIterableForEachMethod() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testEmptyIteratorForEachRemainingMethod() {
    cancellationCheck.doTest(fixName)
  }

  @Test
  fun testSuspendingMapForMethod() {
    cancellationCheck.doTest(fixName)
  }

}
