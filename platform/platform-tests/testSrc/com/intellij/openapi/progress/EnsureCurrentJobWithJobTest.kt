// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EnsureCurrentJobWithJobTest : CancellationTest() {

  @Test
  fun context() {
    currentJobTest { job ->
      assertSame(job, Cancellation.currentJob())
      assertNull(ProgressManager.getGlobalProgressIndicator())

      ensureCurrentJob { currentJob ->
        assertSame(job, Cancellation.currentJob())
        assertSame(job, currentJob)
        assertNull(ProgressManager.getGlobalProgressIndicator())
      }

      assertSame(job, Cancellation.currentJob())
      assertNull(ProgressManager.getGlobalProgressIndicator())
    }
  }

  @Test
  fun cancellation() {
    val t = object : Throwable() {}
    val ce = assertThrows<CancellationException> {
      withJob(Job()) {
        throw assertThrows<JobCanceledException> {
          ensureCurrentJob { currentJob ->
            testNoExceptions()
            currentJob.cancel("", t)
            testExceptionsAndNonCancellableSection()
          }
        }
      }
    }
    assertSame(t, ce.cause)
  }

  @Test
  fun rethrow() {
    currentJobTest {
      testEnsureCurrentJobRethrow()
    }
  }

  @Test
  fun `cancelled by child failure`() {
    val job = Job()
    val t = Throwable()
    val ce = assertThrows<CancellationException> {
      withJob(job) {
        throw assertThrows<JobCanceledException> {
          ensureCurrentJob { currentJob ->
            testNoExceptions()
            Job(parent = currentJob).completeExceptionally(t)
            assertThrows<JobCanceledException> {
              Cancellation.checkCancelled()
            }
            throw assertThrows<JobCanceledException> {
              ProgressManager.checkCanceled()
            }
          }
        }
      }
    }
    assertSame(t, ce.cause)
    assertFalse(job.isActive)
    assertTrue(job.isCancelled)
    assertTrue(job.isCompleted)
  }
}
