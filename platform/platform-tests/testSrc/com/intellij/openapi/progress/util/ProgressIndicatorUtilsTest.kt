// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ProgressIndicatorUtilsTest : BasePlatformTestCase() {
  @Test
  fun awaitWithCheckCanceledShoulToleratePCE() {
    val testCause = RuntimeException("test")
    val testPCE = ProcessCanceledException(testCause)
    try {
      ProgressIndicatorUtils.awaitWithCheckCanceled {
        throw testPCE
      }
      TestCase.fail("Should throw PCE")
    }
    catch (pce: ProcessCanceledException) {
      assertTrue(pce == testPCE || pce.suppressed[0] == testPCE)
    }
  }
}