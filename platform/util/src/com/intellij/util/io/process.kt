// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.openapi.util.IntellijInternalApi
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.BufferedReader
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration

/**
 * This should be used instead of [Process.onExit]`().await()`.
 * @return [Process.exitValue]
 */
suspend fun Process.awaitExit(): Int {
  return loopInterruptible { timeout: Duration ->
    if (timeout.isInfinite()) {
      @Suppress("UsePlatformProcessAwaitExit")
      Attempt.success(waitFor())
    }
    else if (waitFor(timeout.inWholeNanoseconds, TimeUnit.NANOSECONDS)) {
      Attempt.success(exitValue())
    }
    else {
      Attempt.tryAgain()
    }
  }
}

/**
 * Computes and returns result of the [action] which may block for an unforeseeable amount of time.
 *
 * The difference with regular cancellable call is that the computation itself may be non-cooperating in regards
 * with the cancellation: i.e. the computation may _ignore_ the cancellation request, but the wrapper provides
 * cancellability anyway. This is implemented by 'detaching' the slow/hanging/non-cancellable computation, and
 * leaving it running in a background. The detached computation wastes resources -- that is the price for
 * non-cooperative behavior.
 *
 * The [action] does not inherit coroutine context from the calling coroutine, use [withContext] to install proper context if needed.
 * The [action] is executed on a special unlimited dispatcher to avoid starving [Dispatchers.IO], even if [context] is assigned
 * to some other dispatcher.
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
suspend fun <T> computeDetached(
  context: CoroutineContext = EmptyCoroutineContext,
  action: suspend CoroutineScope.() -> T,
): T {
  val deferred = GlobalScope.async(blockingDispatcher + context, block = action)
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
  runInterruptible(blockingDispatcher) {
    readLine()
  }
}