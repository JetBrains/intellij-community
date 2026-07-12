// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/controlFlowExceptionManualRethrow")
class ControlFlowExceptionManualRethrowInspectionTest : ControlFlowExceptionManualRethrowInspectionTestBase() {

  override fun getBasePath(): String {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/controlFlowExceptionManualRethrow"
  }

  override fun getFileExtension(): String {
    return "java"
  }

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
        package com.intellij.diagnostic;
        public final class ControlFlowExceptionsKt {
          public static void rethrowControlFlowException(Throwable t) {}
        }
        """.trimIndent())
  }

  fun testManualGuards() {
    myFixture.testHighlighting("ManualGuards.java")
  }

  fun testReplaceInstanceofGuard() {
    doFixTest()
  }

  fun testReplaceShouldRethrowGuard() {
    doFixTest()
  }
}
