// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.common.initializeTestEnvironment
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

/**
 * @see JUnit3LoggedErrorTest
 * @see JUnit3AndAHalfLoggedErrorTest
 * @see com.intellij.testFramework.junit5.showcase.JUnit5LoggedErrorTest
 */
class JUnit4LoggedErrorTest {

  companion object {

    init {
      initializeTestEnvironment()
    }

    private val LOG = Logger.getInstance(JUnit4LoggedErrorTest::class.java)
  }

  @Rule
  @JvmField
  val testLoggerWatcher: TestRule = TestLoggerFactory.createTestWatcher()

  // It is expected that this test does not fail, and all 4 logged errors are reported as separate test failures.
  @Test
  fun `logged error does not fail the test`() {
    LOG.error(Throwable())
    LOG.error(Throwable("throwable message 1"))
    LOG.error("error with message", Throwable())
    LOG.error("error & throwable with message ", Throwable("throwable message 2"))
  }
}
