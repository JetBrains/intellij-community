// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
private val processWaiter: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(parallelism = Int.MAX_VALUE)
private val stepMs = 10.milliseconds

/**
 * This should be used instead of [Process.onExit]`().await()`.
 * @return [Process.exitValue]
 */
suspend fun Process.awaitExit(): Int {
  // max wait per attempt: 100 * 10ms = 1 second
  repeat(100) { attempt ->
    val ok = runInterruptible(Dispatchers.IO) {
      waitFor((stepMs * attempt).inWholeNanoseconds, TimeUnit.NANOSECONDS)
    }
    if (ok) {
      return exitValue()
    }
  }
  return runInterruptible(processWaiter) {
    waitFor()
  }
}
