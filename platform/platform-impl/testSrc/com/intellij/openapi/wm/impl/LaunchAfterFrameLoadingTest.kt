// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
internal class LaunchAfterFrameLoadingTest {
  @Test
  fun `the task runs once the frame loading completes`(): Unit = timeoutRunBlocking {
    val scope = CoroutineScope(Job())
    val frameLoadingDone = Job()
    val runCount = AtomicInteger()

    val job = launchAfterFrameLoading(scope = scope, frameLoadingDone = frameLoadingDone, isProjectOpen = { true }) {
      runCount.incrementAndGet()
    }
    delay(100.milliseconds)
    assertThat(runCount.get()).isZero()

    frameLoadingDone.complete()
    job.join()
    assertThat(runCount.get()).isEqualTo(1)
    scope.cancel()
  }

  @Test
  fun `the task runs after a cancelled loading when the project stays open`(): Unit = timeoutRunBlocking {
    val scope = CoroutineScope(Job())
    val frameLoadingDone = Job()
    val runCount = AtomicInteger()

    val job = launchAfterFrameLoading(scope = scope, frameLoadingDone = frameLoadingDone, isProjectOpen = { true }) {
      runCount.incrementAndGet()
    }
    frameLoadingDone.cancel()
    job.join()
    assertThat(runCount.get()).isEqualTo(1)
    scope.cancel()
  }

  @Test
  fun `the task does not run after a cancelled loading of a closed project`(): Unit = timeoutRunBlocking {
    val scope = CoroutineScope(Job())
    val frameLoadingDone = Job()
    val runCount = AtomicInteger()

    val job = launchAfterFrameLoading(scope = scope, frameLoadingDone = frameLoadingDone, isProjectOpen = { false }) {
      runCount.incrementAndGet()
    }
    frameLoadingDone.cancel()
    job.join()
    assertThat(runCount.get()).isZero()
    scope.cancel()
  }

  @Test
  fun `the task does not run when the frame scope ends before the loading`(): Unit = timeoutRunBlocking {
    val scope = CoroutineScope(Job())
    val frameLoadingDone = Job()
    val runCount = AtomicInteger()

    val job = launchAfterFrameLoading(scope = scope, frameLoadingDone = frameLoadingDone, isProjectOpen = { true }) {
      runCount.incrementAndGet()
    }
    scope.cancel()
    job.join()
    frameLoadingDone.complete()
    assertThat(runCount.get()).isZero()
  }
}
