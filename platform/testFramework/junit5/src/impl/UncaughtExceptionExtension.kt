// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.fail

/**
 * Fails a test when a thread of the test run ends with an uncaught exception.
 *
 * One [TestUncaughtExceptionHandler] serves the whole engine run, because
 * [Thread.setDefaultUncaughtExceptionHandler] is one slot for the whole JVM. Each test drains the
 * exceptions that arrived since the previous test.
 *
 * Parallel execution changes which test fails. The JVM gives no way to map a thread back to the
 * test that started it, so a test can report the exception of a test that runs at the same time.
 * The run reports every exception one time. The test that fails is a best guess.
 *
 * The JUnit 4 counterpart is `com.intellij.testFramework.UncaughtExceptionsRule`.
 */
@TestOnly
internal class UncaughtExceptionExtension : BeforeEachCallback, AfterEachCallback {

  override fun beforeEach(context: ExtensionContext) {
    TestUncaughtExceptionHandler.getOrInstall(context)
  }

  override fun afterEach(context: ExtensionContext) {
    val handler = TestUncaughtExceptionHandler.getOrInstall(context)
    val displaced = Thread.getDefaultUncaughtExceptionHandler() !== handler
    if (displaced) {
      Thread.setDefaultUncaughtExceptionHandler(handler)
    }
    handler.assertAllExceptionAreCaught()
    if (displaced) {
      fail("The test replaced the default uncaught exception handler and did not restore it. " +
           "The run lost the uncaught exceptions of this test.")
    }
  }
}
