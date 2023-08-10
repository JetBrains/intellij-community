// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * @see com.intellij.testFramework.UncaughtExceptionsRule
 */
@TestOnly
internal class UncaughtExceptionExtension : BeforeEachCallback, AfterEachCallback {

  private var defaultHandler: Thread.UncaughtExceptionHandler? = null

  override fun beforeEach(context: ExtensionContext?) {
    defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler(TestUncaughtExceptionHandler())
  }

  override fun afterEach(context: ExtensionContext?) {
    val testHandler = Thread.getDefaultUncaughtExceptionHandler() as TestUncaughtExceptionHandler
    Thread.setDefaultUncaughtExceptionHandler(defaultHandler)
    testHandler.assertAllExceptionAreCaught()
  }
}
