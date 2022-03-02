// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import kotlinx.coroutines.CancellationException
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
    withJob(job) {
      assertSame(job, Cancellation.currentJob())
    }
    assertNull(Cancellation.currentJob())
    assertFalse(job.isCompleted)
  }

  @Test
  fun cancellation() {
    val t = object : Throwable() {}
    val ce = assertThrows<CancellationException> {
      val job = Job()
      withJob(job) {
        testNoExceptions()
        job.cancel("", t)
        testExceptionsAndNonCancellableSection()
      }
    }
    assertSame(t, ce.cause)
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
    withJob(job) {
      assertDoesNotThrow {
        ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(null)
      }
      job.cancel()
      assertThrows<JobCanceledException> {
        ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(null)
      }
    }
  }
}
