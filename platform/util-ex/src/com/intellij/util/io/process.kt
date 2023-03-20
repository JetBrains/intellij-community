// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.openapi.util.IntellijInternalApi
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.BufferedReader
import java.util.concurrent.CancellationException
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

/**
 * Computes and returns result of the [action] which may block for an unforeseeable amount of time.
 *
 * The [action] does not inherit coroutine context from the calling coroutine, use [withContext] to install proper context if needed.
 * The [action] is executed on a special unlimited dispatcher to avoid starving [Dispatchers.IO].
 * The [action] is cancelled if the calling coroutine is cancelled,
 * but this function immediately resumes with CancellationException without waiting for completion of the [action],
 * which means that this function **breaks structured concurrency**.
 *
 * This function is designed to work with native calls which may un-interruptibly hang.
 * Do not run CPU-bound work computations in [action].
 */
@DelicateCoroutinesApi // require explicit opt-in
@IntellijInternalApi
@Internal
suspend fun <T> computeDetached(action: suspend CoroutineScope.() -> T): T {
  val deferred = GlobalScope.async(processWaiter, block = action)
  try {
    return deferred.await()
  }
  catch (ce: CancellationException) {
    deferred.cancel(ce)
    throw ce
  }
}

/**
 * Reads line in suspendable manner.
 * Might be slow, for high performance consider using separate thread and blocking call
 */
@OptIn(DelicateCoroutinesApi::class)
suspend fun BufferedReader.readLineAsync(): String? = computeDetached {
  runInterruptible(processWaiter) {
    readLine()
  }
}