// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import com.intellij.testFramework.UsefulTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * @see JUnit3LoggedErrorTest
 * @see JUnit4LoggedErrorTest
 * @see com.intellij.testFramework.junit5.showcase.JUnit5LoggedErrorTest
 */
@RunWith(JUnit4::class)
class JUnit3AndAHalfLoggedErrorTest : UsefulTestCase() {

  companion object {
    private val LOG = Logger.getInstance(JUnit3AndAHalfLoggedErrorTest::class.java)
  }

  // It is expected that this test does not fail, and all 4 logged errors are reported as separate test failures.
  @Test
  fun `logged error does not fail the test`() {
    LOG.error(Throwable())
    LOG.error(Throwable("throwable message 1"))
    LOG.error("error with message", Throwable())
    LOG.error("error & throwable with message ", Throwable("throwable message 2"))
  }
}
