// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class CurrentJobTest : CancellationTest() {

  @Test
  fun context() {
    val job = Job()
    assertNull(Cancellation.currentJob())
    withCurrentJob(job) {
      assertSame(job, Cancellation.currentJob())
    }
    assertNull(Cancellation.currentJob())
    assertFalse(job.isCompleted)
  }

  @Test
  fun cancellation() {
    val t = object : Throwable() {}
    val ce = assertThrows<CurrentJobCancellationException> {
      val job = Job()
      withCurrentJob<Unit>(job) {
        testNoExceptions()
        job.cancel("", t)
        testExceptionsAndNonCancellableSection()
      }
    }
    //suppressed until this one is fixed: https://youtrack.jetbrains.com/issue/KT-52379
    @Suppress("AssertBetweenInconvertibleTypes")
    assertSame(t, ce.cause.cause.cause)
  }

  @Disabled("an orphan job is created")
  @Test
  fun `ensureCurrentJob without current job or current indicator`() {
    assertThrows<IllegalStateException> {
      ensureCurrentJob {
        fail()
      }
    }
  }

  @Test
  fun `checkCancelledEvenWithPCEDisabled checks job`() {
    val job = Job()
    withCurrentJob(job) {
      assertDoesNotThrow {
        ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(null)
      }
      job.cancel()
      assertThrows<JobCanceledException> {
        ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(null)
      }
      Cancellation.computeInNonCancelableSection<Unit, Exception> {
        assertDoesNotThrow {
          ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(null)
        }
      }
      assertThrows<JobCanceledException> {
        ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(null)
      }
      ProgressManager.getInstance().computeInNonCancelableSection<Unit, Exception> {
        assertDoesNotThrow {
          ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(null)
        }
      }
      assertThrows<JobCanceledException> {
        ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(null)
      }
    }
  }
}
