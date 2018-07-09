// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency

import com.intellij.util.ExceptionUtil
import com.intellij.util.Function
import org.jetbrains.concurrency.Promise.State
import java.util.concurrent.*
import java.util.function.Consumer

open class AsyncPromise<T> : CancellablePromise<T>, Promise.CompletablePromise<T> {
  private val f: CompletableFuture<T>

  constructor() {
    f = CompletableFuture()
  }

  // used for chaining builders like thenAsync()
  private constructor(w: CompletableFuture<T>) {
    f = w
  }

  override fun isDone(): Boolean = f.isDone
  override fun get(): T? = nullizeCancelled { f.get() }
  override fun get(timeout: Long, unit: TimeUnit?): T? = nullizeCancelled { f.get(timeout, unit) }

  // because of the unorthodox contract: get() should return null for canceled promise
  private fun nullizeCancelled(value: () -> T?): T? {
    if (isCancelled) return null
    return try {
      value()
    }
    catch (e: CancellationException) {
      null
    }
  }

  override fun isCancelled(): Boolean = f.isCancelled

  // because of the unorthodox contract: "double cancel must return false"
  override fun cancel(mayInterruptIfRunning: Boolean): Boolean = !isCancelled && f.cancel(mayInterruptIfRunning)

  override fun cancel() {
    cancel(true)
  }

  override fun getState(): State = if (!f.isDone) State.PENDING else if (f.isCompletedExceptionally) State.REJECTED else State.SUCCEEDED

  override fun onSuccess(handler: Consumer<in T>): Promise<T> {
    val whenComplete = f.whenComplete { value, exception -> if (exception == null) handler.accept(value) }
    return AsyncPromise(whenComplete)
  }

  override fun onError(rejected: Consumer<Throwable>): Promise<T> {
    val whenComplete = f.whenComplete { _, exception ->
      if (exception != null) {
        val toReport = if (exception is CompletionException && exception.cause != null) exception.cause!! else exception
        rejected.accept(toReport)
      }
    }
    return AsyncPromise(whenComplete)
  }

  override fun onProcessed(processed: Consumer<in T>): Promise<T> {
    val whenComplete = f.whenComplete { value, _ -> processed.accept(value) }
    return AsyncPromise(whenComplete)
  }

  override fun blockingGet(timeout: Int, timeUnit: TimeUnit): T? {
    try {
      return get(timeout.toLong(), timeUnit)
    }
    catch (e: ExecutionException) {
      ExceptionUtil.rethrowUnchecked(e.cause)
      throw e
    }
  }

  override fun <SUB_RESULT : Any?> then(done: Function<in T, out SUB_RESULT>): Promise<SUB_RESULT> {
    val thenApply = f.thenApply { t -> done.`fun`(t) }
    return AsyncPromise(thenApply)
  }

  override fun <SUB_RESULT : Any?> thenAsync(doneF: Function<in T, Promise<SUB_RESULT>>): Promise<SUB_RESULT> {
    val convert: (T) -> CompletableFuture<SUB_RESULT> = {
      val promise = doneF.`fun`(it)
      val future = CompletableFuture<SUB_RESULT>()
      promise.onSuccess { value -> future.complete(value) }.onError { error -> future.completeExceptionally(error) }
      future
    }
    val thenCompose = f.thenCompose(convert)
    return AsyncPromise(thenCompose)
  }

  override fun processed(child: Promise<in T>): Promise<T> {
    if (child !is AsyncPromise) {
      return this
    }
    return onSuccess { value -> child.setResult(value) }.onError { error -> child.setError(error) }
  }

  override fun setResult(t: T?) {
    f.complete(t)
  }

  override fun setError(error: Throwable): Boolean = f.completeExceptionally(error)

  fun setError(error: String): Boolean = setError(createError(error))
}

inline fun <T> AsyncPromise<*>.catchError(runnable: () -> T): T? {
  return try {
    runnable()
  }
  catch (e: Throwable) {
    setError(e)
    null
  }
}