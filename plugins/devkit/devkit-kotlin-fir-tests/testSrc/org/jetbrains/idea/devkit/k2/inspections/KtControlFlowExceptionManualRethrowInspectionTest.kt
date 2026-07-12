// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.idea.devkit.inspections.ControlFlowExceptionManualRethrowInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.test.util.invalidateCaches

@TestDataPath("\$CONTENT_ROOT/testData/inspections/controlFlowExceptionManualRethrow")
class KtControlFlowExceptionManualRethrowInspectionTest : ControlFlowExceptionManualRethrowInspectionTestBase() {

  override fun getBasePath(): String {
    return DevkitKtTestsUtil.TESTDATA_PATH + "inspections/controlFlowExceptionManualRethrow"
  }

  override fun getFileExtension(): String {
    return "kt"
  }

  override fun setUp() {
    super.setUp()
    ConfigLibraryUtil.configureKotlinRuntime(module)
    myFixture.addFileToProject("controlFlowExceptions.kt", """
        package com.intellij.diagnostic
        fun rethrowControlFlowException(t: Throwable?) {}
      """.trimIndent())
  }

  override fun tearDown() {
    runAll(
      { runInEdtAndWait { project.invalidateCaches() } },
      { super.tearDown() },
    )
  }

  fun testManualGuards() {
    myFixture.testHighlighting("ManualGuards.kt")
  }

  fun testReplaceIsGuard() {
    doFixTest()
  }

  fun testReplaceShouldRethrowGuard() {
    doFixTest()
  }
}
