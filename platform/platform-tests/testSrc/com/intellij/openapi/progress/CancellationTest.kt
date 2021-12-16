// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.testFramework.ApplicationExtension
import com.intellij.util.concurrency.Semaphore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

class CancellationTest {

  companion object {

    @RegisterExtension
    @JvmField
    val applicationExtension = ApplicationExtension()
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
  fun `checkCanceled delegates to job`(): Unit = runBlocking {
    val pm = ProgressManager.getInstance()
    val lock = Semaphore(1)
    val cancelled = Semaphore(1)
    val job = launch(Dispatchers.IO) {
      assertNull(Cancellation.currentJob())
      withJob { currentJob ->
        assertSame(currentJob, Cancellation.currentJob())
        assertDoesNotThrow {
          ProgressManager.checkCanceled()
        }
        ProgressManager.checkCanceled()
        lock.up()
        cancelled.timeoutWaitUp()
        assertThrows<JobCanceledException> {
          ProgressManager.checkCanceled()
        }
        pm.executeNonCancelableSection {
          assertDoesNotThrow {
            ProgressManager.checkCanceled()
          }
        }
        assertThrows<JobCanceledException> {
          ProgressManager.checkCanceled()
        }
      }
      assertNull(Cancellation.currentJob())
    }
    lock.timeoutWaitUp()
    job.cancel()
    cancelled.up()
    job.timeoutJoin()
  }
}
