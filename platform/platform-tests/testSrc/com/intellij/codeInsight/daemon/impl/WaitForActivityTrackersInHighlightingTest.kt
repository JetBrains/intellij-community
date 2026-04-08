// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.platform.backend.observation.ActivityKey
import com.intellij.platform.backend.observation.trackActivity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/**
 * Verifies that [TestDaemonCodeAnalyzerImpl.waitForAllThingsBeforeDaemonStart]
 * waits for activity trackers to complete before starting highlighting passes.
 */
class WaitForActivityTrackersInHighlightingTest : BasePlatformTestCase() {

  private object TestActivityKey : ActivityKey {
    override val presentableName: @Nls String get() = "Test background activity"
  }

  fun testDoHighlightingWaitsForActivityTrackers() {
    myFixture.configureByText("test.txt", "hello")

    val activityStartedLatch = CountDownLatch(1)
    val activityCompleted = AtomicBoolean(false)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    try {
      // Start a tracked activity that takes some time
      scope.launch {
        project.trackActivity(TestActivityKey) {
          activityStartedLatch.countDown()
          delay(5.seconds)
          activityCompleted.set(true)
        }
      }

      // Wait until the tracked activity has started
      activityStartedLatch.await()

      // doHighlighting() should wait for the activity to complete before running passes
      myFixture.doHighlighting()

      assertTrue("Activity tracker should have completed before highlighting", activityCompleted.get())
    }
    finally {
      scope.cancel()
    }
  }
}
