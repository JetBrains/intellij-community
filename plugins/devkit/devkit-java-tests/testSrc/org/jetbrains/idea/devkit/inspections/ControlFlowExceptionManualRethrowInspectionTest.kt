// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/controlFlowExceptionManualRethrow")
@TestApplication
class ControlFlowExceptionManualRethrowInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val controlFlow by controlFlowExceptionManualRethrowFixture(
    projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/controlFlowExceptionManualRethrow")
  private val myFixture get() = controlFlow.fixture

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
  fun testManualGuards() {
    myFixture.testHighlighting("ManualGuards.java")
  }

  @Test
  fun testReplaceInstanceofGuard() {
    controlFlow.doFixTest()
  }

  @Test
  fun testReplaceShouldRethrowGuard() {
    controlFlow.doFixTest()
  }
}
