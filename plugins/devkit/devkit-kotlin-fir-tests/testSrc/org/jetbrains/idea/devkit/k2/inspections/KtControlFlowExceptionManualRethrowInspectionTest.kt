// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.openapi.application.EDT
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.devkit.inspections.controlFlowExceptionManualRethrowFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.test.util.invalidateCaches
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/controlFlowExceptionManualRethrow")
@TestApplication
class KtControlFlowExceptionManualRethrowInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val controlFlow by controlFlowExceptionManualRethrowFixture(
    projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/controlFlowExceptionManualRethrow")
  private val myFixture get() = controlFlow.fixture

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      ConfigLibraryUtil.configureKotlinRuntime(myFixture.module)
    }
    myFixture.addFileToProject("controlFlowExceptions.kt", """
        package com.intellij.diagnostic
        fun rethrowControlFlowException(t: Throwable?) {}
      """.trimIndent())
  }

  @AfterEach
  fun tearDown(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      myFixture.project.invalidateCaches()
    }
  }

  @Test
  fun testManualGuards() {
    myFixture.testHighlighting("ManualGuards.kt")
  }

  @Test
  fun testReplaceIsGuard() {
    controlFlow.doFixTest()
  }

  @Test
  fun testReplaceShouldRethrowGuard() {
    controlFlow.doFixTest()
  }
}
