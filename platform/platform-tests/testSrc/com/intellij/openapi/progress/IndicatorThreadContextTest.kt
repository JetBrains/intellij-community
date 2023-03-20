// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import kotlinx.coroutines.Job
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class IndicatorThreadContextTest : CancellationTest() {

  @Test
  fun context() {
    indicatorTest {
      assertNull(Cancellation.currentJob())
      assertNotNull(ProgressManager.getGlobalProgressIndicator())

      prepareThreadContextTest { currentJob ->
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
        prepareThreadContextTest { currentJob ->
          testNoExceptions()
          indicator.cancel()
          currentJob.timeoutJoinBlocking() // not immediate because watch job polls indicator every 10ms
          testExceptionsAndNonCancellableSection()
        }
      }
      assertFalse(pce is JobCanceledException)
    }
  }

  @Test
  fun rethrow() {
    indicatorTest {
      testPrepareThreadContextRethrow()
    }
  }

  @Test
  fun `completes normally`() {
    indicatorTest {
      lateinit var currentJob: Job
      val result = prepareThreadContextTest {
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
        prepareThreadContextTest {
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
    val pce = assertThrows<ProcessCanceledException> {
      withIndicator(indicator) {
        throw assertThrows<ProcessCanceledException> {
          prepareThreadContextTest { currentJob ->
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
    val ce = assertInstanceOf<CurrentJobCancellationException>(pce.cause)
    assertSame(t, ce.originalCancellationException.cause)
  }
}
