// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.openapi.diagnostic.Logger
import org.junit.jupiter.api.Test

/**
 * @see com.intellij.openapi.diagnostic.JUnit3LoggedErrorTest
 * @see com.intellij.openapi.diagnostic.JUnit3AndAHalfLoggedErrorTest
 * @see com.intellij.openapi.diagnostic.JUnit4LoggedErrorTest
 */
class JUnit5LoggedErrorTest {

  companion object {
    private val LOG = Logger.getInstance(JUnit5ApplicationTest::class.java)
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
