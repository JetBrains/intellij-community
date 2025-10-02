// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections.quickfix

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevKitBundle
import org.jetbrains.idea.devkit.inspections.CancellationCheckInLoopsInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

@TestDataPath("\$CONTENT_ROOT/testData/inspections/insertCancellationCheckFix")
class KtInsertCancellationCheckFixTest : CancellationCheckInLoopsInspectionTestBase(), ExpectedPluginModeProvider {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K1

  override fun getFileExtension(): String = "kt"

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/insertCancellationCheckFix"

  override fun setUp() {
    setUpWithKotlinPlugin { super.setUp() }
    myFixture.addFileToProject(
      "Coroutines.kt",
      //language=kotlin
      """
      package com.intellij.openapi.progress

      suspend fun checkCancelled() { }
      """.trimIndent())
  }

  private val fixName = DevKitBundle.message("inspection.insert.cancellation.check.fix.message")

  // loops:

  fun testBlockDoWhileLoop() {
    doTest(fixName)
  }

  fun testBlockForEachLoop() {
    doTest(fixName)
  }

  fun testBlockWhileLoop() {
    doTest(fixName)
  }

  fun testEmptyDoWhileLoop() {
    doTest(fixName)
  }

  fun testEmptyForEachLoop() {
    doTest(fixName)
  }

  fun testEmptyWhileLoop() {
    doTest(fixName)
  }

  fun testSingleLineDoWhileLoop() {
    doTest(fixName)
  }

  fun testSingleLineForEachLoop() {
    doTest(fixName)
  }

  fun testSingleLineWhileLoop() {
    doTest(fixName)
  }

  fun testNoBodyForEachLoop() {
    doTest(fixName)
  }

  fun testNoBodyWhileLoop() {
    doTest(fixName)
  }

  fun testSuspendingDoWhileLoop() {
    doTest(fixName)
  }

  fun testSuspendingForEachLoop() {
    doTest(fixName)
  }

  fun testSuspendingLambdaForEachLoop() {
    doTest(fixName)
  }

  fun testSuspendingWhileLoop() {
    doTest(fixName)
  }

  // loop methods:

  fun testBlockIterableForEachMethod() {
    doTest(fixName)
  }

  fun testEmptyIteratorForEachRemainingMethod() {
    doTest(fixName)
  }

  fun testSuspendingMapForMethod() {
    doTest(fixName)
  }

}
