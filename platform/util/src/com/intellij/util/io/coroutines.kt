// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.util.concurrency.Semaphore
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.time.Duration

suspend fun <T> Future<T>.await(): T {
  if (isDone) {
    try {
      @Suppress("BlockingMethodInNonBlockingContext")
      return get()
    }
    catch (e: ExecutionException) {
      throw e.cause ?: e
    }
  }
  return loopInterruptible { timeout: Duration ->
    if (timeout.isInfinite()) {
      try {
        Attempt.success(get())
      }
      catch (e: ExecutionException) {
        throw e.cause ?: e
      }
    }
    else {
      try {
        Attempt.success(get(timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS))
      }
      catch (_: TimeoutException) {
        Attempt.tryAgain()
      }
      catch (e: ExecutionException) {
        throw e.cause ?: e
      }
    }
  }
}

suspend fun Semaphore.awaitFor() {
  if (isUp) {
    return
  }
  loopInterruptible { timeout: Duration ->
    if (timeout.isInfinite()) {
      Attempt.success(waitForUnsafe())
    }
    else if (waitForUnsafe(timeout.inWholeMilliseconds)) {
      Attempt.success(Unit)
    }
    else {
      Attempt.tryAgain()
    }
  }
}
