// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.concurrency.currentThreadContextOrNull
import com.intellij.openapi.application.contextModality
import com.intellij.platform.util.progress.progressReporter
import com.intellij.util.parent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class IndicatorThreadContextTest : CancellationTest() {

  @Test
  fun context() {
    indicatorTest {
      assertNull(currentThreadContextOrNull())
      val indicator = assertNotNull(ProgressManager.getGlobalProgressIndicator())

      prepareThreadContext { ctx ->
        assertNull(currentThreadContextOrNull())
        assertNull(ProgressManager.getGlobalProgressIndicator())
        assertNull(ctx.job.parent())
        assertSame(indicator.modalityState, ctx.contextModality())
        assertNull(ctx.progressReporter)
        assertEquals(2, ctx.fold(0) { acc, _ -> acc + 1 })
      }

      assertNull(currentThreadContextOrNull())
      assertNotNull(ProgressManager.getGlobalProgressIndicator())
    }
  }

  @Test
  fun cancellation() {
    val indicator = EmptyProgressIndicator()
    withIndicator(indicator) {
      assertThrows<CeProcessCanceledException> {
        prepareThreadContextTest { currentJob ->
          testNoExceptions()
          indicator.cancel()
          currentJob.timeoutJoinBlocking() // not immediate because watch job polls indicator every 10ms
          testExceptionsAndNonCancellableSection()
        }
      }
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
    val pce = assertThrows<CeProcessCanceledException> {
      withIndicator(indicator) {
        throw assertThrows<CeProcessCanceledException> {
          prepareThreadContextTest { currentJob ->
            testNoExceptions()
            Job(parent = currentJob).completeExceptionally(t)
            assertThrows<CeProcessCanceledException> {
              Cancellation.checkCancelled()
            }
            assertFalse(indicator.isCanceled)
            throw assertThrows<CeProcessCanceledException> {
              ProgressManager.checkCanceled()
            }
          }
        }
      }
    }
    val ce = assertInstanceOf<CancellationException>(pce.cause)
    assertSame(t, ce.cause)
  }
}
