// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ExceptionUtilRt
import com.intellij.util.Function
import org.jetbrains.concurrency.Promise.State
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

open class AsyncPromise<T> private constructor(f: CompletableFuture<T>,
                                               private val hasErrorHandler: AtomicBoolean,
                                               addExceptionHandler: Boolean) : CancellablePromise<T>, InternalPromiseUtil.CompletablePromise<T> {
  private val f: CompletableFuture<T>
  private companion object {
    val CANCELED = CancellationException()
  }

  constructor() : this(CompletableFuture(), AtomicBoolean(), addExceptionHandler = false)

  init {
    // cannot be performed outside of AsyncPromise constructor because this instance `hasErrorHandler` must be checked
    this.f = when {
      addExceptionHandler -> {
        f.exceptionally { originalError ->
          val error = (originalError as? CompletionException)?.cause ?: originalError
          if (!hasErrorHandler.get()) {
            Logger.getInstance(AsyncPromise::class.java).errorIfNotMessage((error as? CompletionException)?.cause ?: error)
          }

          throw error
        }
      }
      else -> f
    }
  }

  override fun isDone(): Boolean = f.isDone

  override fun get(): T? = nullizeCancelled { f.get() }

  override fun get(timeout: Long, unit: TimeUnit): T? = nullizeCancelled { f.get(timeout, unit) }

  // because of the contract: get() should return null for canceled promise
  private inline fun nullizeCancelled(value: () -> T?): T? {
    if (isCancelled) {
      return null
    }

    return try {
      value()
    }
    catch (e: CancellationException) {
      null
    }
  }

  override fun isCancelled(): Boolean = f.isCancelled

  // because of the unorthodox contract: "double cancel must return false"
  override fun cancel(mayInterruptIfRunning: Boolean): Boolean = !isCancelled && f.completeExceptionally(CANCELED)

  override fun cancel() {
    cancel(true)
  }

  override fun getState(): State {
    return when {
      !f.isDone -> State.PENDING
      f.isCompletedExceptionally -> State.REJECTED
      else -> State.SUCCEEDED
    }
  }

  override fun onSuccess(handler: Consumer<in T>): Promise<T> {
    return AsyncPromise(f.whenComplete { value, error ->
      if (error != null) {
        throw error
      }

      if (!InternalPromiseUtil.isHandlerObsolete(handler)) {
        handler.accept(value)
      }
    }, hasErrorHandler, addExceptionHandler = true)
  }

  override fun onError(rejected: Consumer<in Throwable>): Promise<T> {
    hasErrorHandler.set(true)
    return AsyncPromise(f.whenComplete { _, exception ->
      if (exception != null) {
        if (!InternalPromiseUtil.isHandlerObsolete(rejected)) {
          rejected.accept((exception as? CompletionException)?.cause ?: exception)
        }
      }
    }, hasErrorHandler, addExceptionHandler = false)
  }

  override fun onProcessed(processed: Consumer<in T>): Promise<T> {
    hasErrorHandler.set(true)
    return AsyncPromise(f.whenComplete { value, _ ->
      if (!InternalPromiseUtil.isHandlerObsolete(processed)) {
        processed.accept(value)
      }
    }, hasErrorHandler, addExceptionHandler = true)
  }

  @Throws(TimeoutException::class)
  override fun blockingGet(timeout: Int, timeUnit: TimeUnit): T? {
    try {
      return get(timeout.toLong(), timeUnit)
    }
    catch (e: ExecutionException) {
      if (e.cause === InternalPromiseUtil.OBSOLETE_ERROR) {
        return null
      }

      ExceptionUtilRt.rethrowUnchecked(e.cause)
      throw e
    }
  }

  override fun <SUB_RESULT : Any?> then(done: Function<in T, out SUB_RESULT>): Promise<SUB_RESULT> {
    return AsyncPromise(f.thenApply { done.`fun`(it) }, hasErrorHandler, addExceptionHandler = true)
  }

  override fun <SUB_RESULT : Any?> thenAsync(doneF: Function<in T, out Promise<SUB_RESULT>>): Promise<SUB_RESULT> {
    return AsyncPromise(f.thenCompose {
      val promise = doneF.`fun`(it)
      val future = CompletableFuture<SUB_RESULT>()
      promise
        .onSuccess { value -> future.complete(value) }
        .onError { error -> future.completeExceptionally(error) }
      future
    }, hasErrorHandler, addExceptionHandler = true)
  }

  override fun processed(child: Promise<in T>): Promise<T> {
    if (child !is AsyncPromise) {
      return this
    }

    return onSuccess { child.setResult(it) }
      .onError { child.setError(it) }
  }

  override fun setResult(t: T?) {
    f.complete(t)
  }

  override fun setError(error: Throwable): Boolean {
    if (!f.completeExceptionally(error)) {
      return false
    }

    if (!hasErrorHandler.get()) {
      Logger.getInstance(AsyncPromise::class.java).errorIfNotMessage(error)
    }
    return true
  }

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
