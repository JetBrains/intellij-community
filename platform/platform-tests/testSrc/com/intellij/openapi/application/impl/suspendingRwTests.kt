// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ReadAction.CannotReadException
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.PceCancellationException
import com.intellij.openapi.progress.ProcessCanceledException
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import kotlin.coroutines.cancellation.CancellationException

internal suspend fun testRwRethrow(rw: suspend (() -> Nothing) -> Nothing) {
  testRwRethrow(object : Throwable() {}, rw)
  testRwRethrow(CancellationException(), rw)
  testRwRethrow(ProcessCanceledException(), rw)
  testRwRethrow(CannotReadException(), rw)
}

private suspend inline fun <reified T : Throwable> testRwRethrow(t: T, noinline rw: suspend (() -> Nothing) -> Nothing) {
  lateinit var readJob: Job
  val thrown = assertThrows<T> {
    rw {
      readJob = requireNotNull(Cancellation.currentJob())
      throw t
    }
  }
  val cause = thrown.cause
  if (cause != null) {
    assertSame(t, cause) // kotlin trace recovery via [cause]
  }
  else {
    assertSame(t, thrown)
  }
  assertTrue(readJob.isCompleted)
  assertTrue(readJob.isCancelled)
}

private suspend inline fun <reified T : ProcessCanceledException> testRwRethrow(pce: T, noinline rw: suspend (() -> Nothing) -> Nothing) {
  lateinit var readJob: Job
  val thrown = assertThrows<PceCancellationException> {
    rw {
      readJob = requireNotNull(Cancellation.currentJob())
      throw pce
    }
  }
  assertSame(pce, thrown.cause)
  assertTrue(readJob.isCompleted)
  assertTrue(readJob.isCancelled)
}
