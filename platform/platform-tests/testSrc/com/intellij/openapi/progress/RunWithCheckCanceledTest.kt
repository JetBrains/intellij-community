// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.progress.util.runWithCheckCanceled
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@OptIn(DelicateCoroutinesApi::class)
class RunWithCheckCanceledTest : CancellationTest() {

  @Test
  fun `current job cancellation cancels blocking waiter and detached action`(): Unit = timeoutRunBlocking {
    val blockingJob = launch {
      doTestCancellation<CeProcessCanceledException> {
        this@launch.cancel()
      }
    }

    blockingJob.join()
    assertTrue(blockingJob.isCancelled)
  }

  @Test
  fun `indicator cancellation cancels blocking waiter and detached action`() {
    val indicator = EmptyProgressIndicator()
    withIndicator(indicator) {
      doTestCancellation<ProcessCanceledException> {
        indicator.cancel()
      }
    }
    assertTrue(indicator.isCanceled)
  }

  private inline fun <reified T : Throwable> doTestCancellation(crossinline cancel: () -> Unit) {
    val detachedJob = AtomicReference<Job>()
    val detachedStarted = CountDownLatch(1)
    val detachedFinished = CountDownLatch(1)

    val cancelThread = Thread {
      check(detachedStarted.await(10, TimeUnit.SECONDS))
      cancel()
    }
    cancelThread.start()

    assertThrows<T> {
      runWithCheckCanceled {
        detachedJob.set(coroutineContext.job)
        detachedStarted.countDown()
        try {
          awaitCancellation()
        }
        finally {
          detachedFinished.countDown()
        }
      }
    }

    cancelThread.join(TimeUnit.SECONDS.toMillis(10))
    assertTrue(!cancelThread.isAlive)
    assertTrue(detachedFinished.await(10, TimeUnit.SECONDS))
    assertTrue(detachedJob.get().isCancelled)
  }
}
