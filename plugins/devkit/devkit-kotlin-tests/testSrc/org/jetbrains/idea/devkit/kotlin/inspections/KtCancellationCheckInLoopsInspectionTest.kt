// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.CancellationCheckInLoopsInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil


@TestDataPath("\$CONTENT_ROOT/testData/inspections/cancellationCheckInLoops")
class KtCancellationCheckInLoopsInspectionTest : CancellationCheckInLoopsInspectionTestBase() {

  override fun getFileExtension(): String = "kt"

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/cancellationCheckInLoops"

  override fun setUp() {
    super.setUp()
    myFixture.addFileToProject(
      "Coroutines.kt",
      //language=kotlin
      """
      package com.intellij.openapi.progress

      suspend fun checkCancelled() { }
      """.trimIndent())
  }

  fun testRunCondition() {
    doTest()
  }

  fun testRunConditionOnSuperMethod() {
    doTest()
  }

  fun testSuspendingContext() {
    doTest()
  }

  fun testPresentCancellationCheck() {
    doTest()
  }

  fun testCancellationCheckPresentNotInFirstLine() {
    doTest()
  }

  fun testForEachLoops() {
    doTest()
  }

  fun testWhileLoops() {
    doTest()
  }

  fun testDoWhileLoops() {
    doTest()
  }

  fun testNestedLoops() {
    doTest()
  }

}
