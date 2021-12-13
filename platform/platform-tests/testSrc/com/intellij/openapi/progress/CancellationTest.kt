// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.testFramework.ApplicationRule
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.Semaphore
import junit.framework.TestCase.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.ClassRule
import org.junit.Test

class CancellationTest {

  companion object {

    @ClassRule
    @JvmField
    val application: ApplicationRule = ApplicationRule()
  }

  @Test
  fun `current job`() {
    val job = Job()
    assertNull(Cancellation.currentJob())
    withJob(job) {
      assertSame(job, Cancellation.currentJob())
    }
    assertNull(Cancellation.currentJob())
  }

  @Test
  fun `current coroutine job`(): Unit = runBlocking {
    assertNull(Cancellation.currentJob())
    val job = coroutineContext.job
    withJob {
      assertSame(job, Cancellation.currentJob())
    }
    assertNull(Cancellation.currentJob())
  }

  @Test
  fun `job cancellation`() {
    val pm = ProgressManager.getInstance()
    val lock = Semaphore(1)
    val job = Job()
    val cancelled = Semaphore(1)
    val future = AppExecutorUtil.getAppExecutorService().submit {
      withJob(job) {
        ProgressManager.checkCanceled()
        lock.up()
        cancelled.timeoutWaitUp()
        assertCheckCanceledThrows()
        pm.executeNonCancelableSection {
          ProgressManager.checkCanceled()
        }
        assertCheckCanceledThrows()
      }
    }
    lock.timeoutWaitUp()
    job.cancel()
    cancelled.up()
    waitAssertCompletedNormally(future)
  }

  private fun assertCheckCanceledThrows() {
    try {
      ProgressManager.checkCanceled()
      fail()
    }
    catch (e: JobCanceledException) {
    }
  }
}
