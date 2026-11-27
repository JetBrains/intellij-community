// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.testFramework.common.checkJavaSwingTimersAreDisposed
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Checks that a test leaves no running Swing timer. A test that runs beside other tests ([runsBesideOtherTests]) is not
 * checked, because the timers of the other tests would count as its timers.
 *
 * JUnit runs the isolated classes of a parallel run after the other classes. So before an isolated class, a running timer
 * is a timer that a class beside other tests left, and the isolated class reports it before its own tests.
 */
@TestOnly
internal class SwingTimerWatcherExtension : BeforeAllCallback, AfterEachCallback {

  override fun beforeAll(context: ExtensionContext) {
    if (!context.isParallelRun() || context.runsBesideOtherTests()) {
      return
    }
    try {
      checkJavaSwingTimersAreDisposed()
    }
    catch (e: AssertionError) {
      throw AssertionError("A class that ran beside other tests of the parallel run left a Swing timer: ${e.message}", e)
    }
  }

  override fun afterEach(context: ExtensionContext) {
    if (!context.runsBesideOtherTests()) {
      checkJavaSwingTimersAreDisposed()
    }
  }
}
