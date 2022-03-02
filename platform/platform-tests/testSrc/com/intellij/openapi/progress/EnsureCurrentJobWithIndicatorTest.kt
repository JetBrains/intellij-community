// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EnsureCurrentJobWithIndicatorTest : CancellationTest() {

  @Test
  fun context() {
    indicatorTest {
      assertNull(Cancellation.currentJob())
      assertNotNull(ProgressManager.getGlobalProgressIndicator())

      ensureCurrentJob { currentJob ->
        assertSame(currentJob, Cancellation.currentJob())
        assertNull(ProgressManager.getGlobalProgressIndicator())
      }

      assertNull(Cancellation.currentJob())
      assertNotNull(ProgressManager.getGlobalProgressIndicator())
    }
  }

  @Test
  fun cancellation() {
    val indicator = EmptyProgressIndicator()
    withIndicator(indicator) {
      val pce = assertThrows<ProcessCanceledException> {
        ensureCurrentJob { currentJob ->
          testNoExceptions()
          indicator.cancel()
          currentJob.timeoutJoinBlocking() // not immediate because watch job polls indicator every 10ms
          val ce = assertThrows<CancellationException> {
            currentJob.ensureActive()
          }
          assertInstanceOf<ProcessCanceledException>(ce.cause)

          val jce = assertThrows<JobCanceledException> {
            Cancellation.checkCancelled()
          }
          assertInstanceOf<ProcessCanceledException>(jce.cause.cause)

          throw assertThrows<JobCanceledException> {
            ProgressManager.checkCanceled()
          }
        }
      }
      assertFalse(pce is JobCanceledException)
    }
  }

  @Test
  fun rethrow() {
    indicatorTest {
      testEnsureCurrentJobRethrow()
    }
  }

  @Test
  fun `completes normally`() {
    indicatorTest {
      lateinit var currentJob: Job
      val result = ensureCurrentJob {
        currentJob = it
        42
      }
      assertEquals(42, result)
      currentJob.timeoutJoinBlocking()
      assertFalse(currentJob.isCancelled)
    }
  }

  @Test
  fun `cancelled on exception`() {
    indicatorTest {
      val t = Throwable()
      lateinit var currentJob: Job
      val thrown = assertThrows<Throwable> {
        ensureCurrentJob {
          currentJob = it
          throw t
        }
      }
      assertSame(t, thrown)
      currentJob.timeoutJoinBlocking()
      assertTrue(currentJob.isCancelled)
    }
  }

  @Test
  fun `cancelled by child failure`() {
    val t = Throwable()
    val indicator = EmptyProgressIndicator()
    val ce = assertThrows<ProcessCanceledException> {
      withIndicator(indicator) {
        throw assertThrows<ProcessCanceledException> {
          ensureCurrentJob { currentJob ->
            testNoExceptions()
            Job(parent = currentJob).completeExceptionally(t)
            assertThrows<JobCanceledException> {
              Cancellation.checkCancelled()
            }
            assertFalse(indicator.isCanceled)
            throw assertThrows<JobCanceledException> {
              ProgressManager.checkCanceled()
            }
          }
        }
      }
    }
    assertSame(t, ce.cause)
  }
}
