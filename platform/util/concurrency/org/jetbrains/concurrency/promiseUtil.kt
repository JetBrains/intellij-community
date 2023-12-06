// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("Promises")
package org.jetbrains.concurrency

import com.intellij.openapi.progress.ProcessCanceledException
import kotlinx.coroutines.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Converts this promise to an instance of [CompletableFuture].
 * Whenever the resulting future is canceled, this promise is canceled as well
 * (if the promise is also an instance of [Future]).
 */
fun <T> Promise<T>.asCompletableFuture(): CompletableFuture<T> {
  return when {
    this is AsyncPromise<T> -> this.f
    this is Future<*> && isDone -> // Fast path if already completed
      try {
        CompletableFuture.completedFuture(this.getResultOrThrowError())
      }
      catch (e: Throwable) {
        CompletableFuture.failedFuture(e)
      }
    else -> CompletableFuture<T>().let { future ->
      onSuccess { future.complete(it) }
      onError { future.completeExceptionally(it) }
      when (this) {
        is Future<*> -> future.whenComplete { _, _ -> this.cancel(false) }
        else -> future
      }
    }
  }
}

// Mostly reflects asDeferred/await implementations from 'kotlinx.coroutines.future'.

/**
 * Converts this promise to an instance of [Deferred].
 * Whenever the resulting deferred is canceled, this promise is canceled as well
 * (if the promise is also an instance of [Future]).
 *
 * NOTE that `promise.asDeferred().await()` is different from `promise.await()` w.r.t. cancellation,
 * see the description of [await] for the details.
 */
@Suppress("DeferredIsResult")
fun <T> Promise<T>.asDeferred(): Deferred<T> = asDeferredInternal()

@Suppress("DeferredIsResult")
internal fun <T> Promise<T>.asDeferredInternal(): Deferred<T> {
  val deferred = CompletableDeferred<T>()
  if (this is Future<*> && isDone) { // Fast path if already completed
    try {
      deferred.complete(this.getResultOrThrowError())
    }
    catch (e: Throwable) {
      deferred.completeExceptionallyOrCancel(e)
    }
  }
  else {
    onSuccess { deferred.complete(it) }
    onError { deferred.completeExceptionallyOrCancel(it) }
    if (this is Future<*>) deferred.invokeOnCompletion { this.cancel(false) }
  }
  return deferred
}

/**
 * Awaits for completion of the promise without blocking a thread.
 *
 * This suspending function is cancellable.
 * If the [Job] of the current coroutine is canceled
 * or completed while this suspending function is waiting, this function stops waiting for the promise
 * and immediately resumes with [CancellationException][kotlinx.coroutines.CancellationException].
 *
 * This method is intended to be used with one-shot promises, so that on coroutine cancellation
 * a promise is canceled as well (if the promise is also an instance of [Future]).
 * If cancelling the given promise is undesired, `promise.asDeferred().await()` should be used instead.
 */
suspend fun <T> Promise<T>.await(): T {
  // fast path if already completed
  if (this is Future<*> && isDone) {
    return this.getResultOrThrowError()
  }
  else {
    // slow path - suspend
    return suspendCancellableCoroutine { continuation ->
      onSuccess(continuation::resume)
      onError(continuation::resumeWithExceptionOrCancel)
      if (this is Future<*>) {
        continuation.invokeOnCancellation { this.cancel(false) }
      }
    }
  }
}

private fun <T> Future<*>.getResultOrThrowError(): T {
  return try {
    @Suppress("UNCHECKED_CAST")
    get() as T
  }
  catch (e: ExecutionException) {
    // unwrap the original cause from ExecutionException
    throw e.cause ?: e
  }
}

private fun CompletableDeferred<*>.completeExceptionallyOrCancel(t: Throwable) {
  when (t) {
    is ProcessCanceledException -> this.cancel(CancellationException(t))
    is CancellationException -> this.cancel(t)
    else -> this.completeExceptionally(t)
  }
}

private fun CancellableContinuation<*>.resumeWithExceptionOrCancel(t: Throwable) {
  when (t) {
    is ProcessCanceledException -> this.cancel(t)
    is CancellationException -> this.cancel(t)
    else -> this.resumeWithException(t)
  }
}
