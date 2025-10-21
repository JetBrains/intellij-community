// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.CancellationCheckInLoopsInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

@TestDataPath("\$CONTENT_ROOT/testData/inspections/cancellationCheckInLoops")
class KtCancellationCheckInLoopsInspectionTest : CancellationCheckInLoopsInspectionTestBase(), ExpectedPluginModeProvider {
  override val pluginMode: KotlinPluginMode = KotlinPluginMode.K1

  override fun getFileExtension(): String = "kt"

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/cancellationCheckInLoops"

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

  fun testLoopMethods() {
    doTest()
  }

  fun testNestedLoopMethods() {
    doTest()
  }

}
