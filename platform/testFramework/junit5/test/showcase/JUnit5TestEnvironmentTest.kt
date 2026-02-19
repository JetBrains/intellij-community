// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.testFramework.common.isTestEnvironmentInitialized
import com.intellij.testFramework.junit5.impl.TestUncaughtExceptionHandler
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import kotlin.concurrent.thread

class JUnit5TestEnvironmentTest {

  @Test
  fun `test environment is initialized`() {
    Assertions.assertTrue(isTestEnvironmentInitialized) {
      "com.intellij.testFramework.common.TestEnvironmentKt#initializeTestEnvironment was not invoked by the session listener"
    }
  }

  @Test
  fun `uncaught exception handler is installed`() {
    Assertions.assertInstanceOf(TestUncaughtExceptionHandler::class.java, Thread.getDefaultUncaughtExceptionHandler())
  }

  @Disabled("this test always fails to make point")
  @Test
  fun `uncaught exception in another thread fails the test`() {
    thread {
      throw Throwable("this exception still fails the test")
    }.join()
  }
}
