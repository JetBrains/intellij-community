// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.testFramework.TestLoggerFactory
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import java.util.*

/**
 * This extension re-throws logged errors after the test has ended.
 */
@TestOnly
internal class TestLoggerExtension : BeforeTestExecutionCallback, TestWatcher {

  override fun beforeTestExecution(context: ExtensionContext) {
    TestLoggerFactory.onTestStarted()
  }

  override fun testSuccessful(context: ExtensionContext) {
    TestLoggerFactory.onTestFinished(true, context.uniqueId)
  }

  override fun testDisabled(context: ExtensionContext, reason: Optional<String>?) {
    TestLoggerFactory.onTestFinished(true, context.uniqueId)
  }

  override fun testAborted(context: ExtensionContext, cause: Throwable?) {
    testFailed(context, cause)
  }

  override fun testFailed(context: ExtensionContext, cause: Throwable?) {
    TestLoggerFactory.logTestFailure(cause)
    TestLoggerFactory.onTestFinished(false, context.uniqueId)
  }
}
