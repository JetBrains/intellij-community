// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Holds the event logging thread of each recorder until the IDE start is done,
 * so the first event does not build the sensitive data validator while the first project frame and its editors load.
 * The hold ends when the application reports the start, the welcome screen or the shutdown, and in any case after a timeout.
 * A unit test and a headless application are never held.
 */
@ApiStatus.Internal
object StatisticsStartupHold {
  private val released = CountDownLatch(1)

  fun release() {
    released.countDown()
  }

  fun await() {
    if (released.count == 0L) {
      return
    }
    val application = ApplicationManager.getApplication()
    if (application == null || application.isUnitTestMode || application.isHeadlessEnvironment) {
      return
    }
    try {
      released.await(20, TimeUnit.SECONDS)
    }
    catch (_: InterruptedException) {
      Thread.currentThread().interrupt()
    }
  }

  internal class Listener : AppLifecycleListener {
    override fun appStarted() {
      release()
    }

    override fun welcomeScreenDisplayed() {
      release()
    }

    override fun appClosing() {
      release()
    }
  }
}
