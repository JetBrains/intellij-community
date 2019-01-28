// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Mostly reflects asDeferred/await implementations from 'kotlinx.coroutines.future'.

/**
 * Converts this promise to an instance of [Deferred].
 * Whenever the resulting deferred is cancelled, this promise is cancelled as well
 * (provided that the promise is also an instance of [Future]).
 *
 * NOTE that `promise.asDeferred().await()` is different from `promise.await()` w.r.t. cancellation,
 * see the description of [await] for the details.
 */
fun <T> Promise<T>.asDeferred(): Deferred<T> = CompletableDeferred<T>().also { deferred ->
  if (this is Future<*> && isDone()) { // Fast path if already completed
    try {
      deferred.complete(this.getResultOrThrowError())
    }
    catch (e: Throwable) {
      deferred.completeExceptionally(e)
    }
  }
  else {
    onSuccess { deferred.complete(it) }
    onError { deferred.completeExceptionally(it) }
    if (this is Future<*>) deferred.invokeOnCompletion { this.cancel(false) }
  }
}

/**
 * Awaits for completion of the promise without blocking a thread.
 *
 * This suspending function is cancellable. If the [Job] of the current coroutine is cancelled
 * or completed while this suspending function is waiting, this function stops waiting for the promise
 * and immediately resumes with [CancellationException][kotlinx.coroutines.CancellationException].
 *
 * This method is intended to be used with one-shot promises, so that on coroutine cancellation
 * a promise is cancelled as well (provided that the promise is also an instance of [Future]).
 * If cancelling the given promise is undesired, `promise.asDeferred().await()` should be used instead.
 */
suspend fun <T> Promise<T>.await(): T =
  if (this is Future<*> && isDone()) { // Fast path if already completed
    this.getResultOrThrowError()
  }
  else { // slow path -- suspend
    suspendCancellableCoroutine { cont ->
      onSuccess { cont.resume(it) }
      onError { cont.resumeWithException(it) }
      if (this is Future<*>) cont.invokeOnCancellation { this.cancel(false) }
    }
  }

@Throws(Throwable::class)
private fun <T> Future<*>.getResultOrThrowError(): T =
  try {
    @Suppress("UNCHECKED_CAST")
    get() as T
  }
  catch (e: ExecutionException) {
    throw e.cause ?: e // unwrap original cause from ExecutionException
  }
