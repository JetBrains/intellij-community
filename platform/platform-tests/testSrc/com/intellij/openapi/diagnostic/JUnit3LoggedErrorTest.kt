// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import com.intellij.testFramework.UsefulTestCase

/**
 * @see JUnit3AndAHalfLoggedErrorTest
 * @see JUnit4LoggedErrorTest
 * @see com.intellij.testFramework.junit5.showcase.JUnit5LoggedErrorTest
 */
class JUnit3LoggedErrorTest : UsefulTestCase() {

  companion object {
    private val LOG = Logger.getInstance(JUnit3LoggedErrorTest::class.java)
  }

  // It is expected that this test does not fail, and all 4 logged errors are reported as separate test failures.
  fun `test logged error does not fail the test`() {
    LOG.error(Throwable())
    LOG.error(Throwable("throwable message 1"))
    LOG.error("error with message", Throwable())
    LOG.error("error & throwable with message ", Throwable("throwable message 2"))
  }
}
