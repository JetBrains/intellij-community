// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class FrameLoadingDoneTest {
  @Test
  fun `loading ends when the foreground job completes`() {
    val foregroundJob = Job()
    val done = createFrameLoadingDone(foregroundJob)
    assertThat(done.isCompleted).isFalse()

    foregroundJob.complete()

    assertThat(done.isCompleted).isTrue()
    assertThat(done.isCancelled).isFalse()
  }

  @Test
  fun `cancelled loading cancels the foreground job`() {
    val foregroundJob = Job()
    val done = createFrameLoadingDone(foregroundJob)

    done.cancel(CancellationException("ESC key pressed"))

    assertThat(foregroundJob.isCancelled).isTrue()
  }

  @Test
  fun `failed foreground job cancels the loading`() {
    val foregroundJob = Job()
    val done = createFrameLoadingDone(foregroundJob)

    foregroundJob.completeExceptionally(IllegalStateException("frame creation failed"))

    assertThat(done.isCancelled).isTrue()
  }

  @Test
  fun `loading of a welcome frame ends when its content is painted and the frame helper is initialized`(): Unit = timeoutRunBlocking {
    val foregroundJob = Job()
    val done = createFrameLoadingDone(foregroundJob)
    val frameHelperInitJob = Job()
    val contentPainted = CompletableDeferred<Unit>()
    completeFrameLoadingOnContentPainted(
      scope = this,
      done = done,
      foregroundJob = foregroundJob,
      frameHelperInitJob = frameHelperInitJob,
      contentPainted = contentPainted,
    )

    contentPainted.complete(Unit)
    yield()
    assertThat(done.isCompleted).isFalse()

    frameHelperInitJob.complete()
    done.join()

    assertThat(done.isCancelled).isFalse()
    assertThat(foregroundJob.isActive).isTrue()
    foregroundJob.complete()
  }

  @Test
  fun `welcome waiter stops when the foreground job completes first`(): Unit = timeoutRunBlocking {
    val foregroundJob = Job()
    val done = createFrameLoadingDone(foregroundJob)
    // the waiter is a child of this scope, so the test times out if the waiter is not cancelled
    completeFrameLoadingOnContentPainted(
      scope = this,
      done = done,
      foregroundJob = foregroundJob,
      frameHelperInitJob = Job(),
      contentPainted = CompletableDeferred(),
    )

    foregroundJob.complete()

    done.join()
    assertThat(done.isCancelled).isFalse()
  }
}
