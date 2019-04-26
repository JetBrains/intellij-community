// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency

import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ExceptionUtilRt
import com.intellij.util.Function
import org.jetbrains.concurrency.Promise.State
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

open class AsyncPromise<T> private constructor(private val f: CompletableFuture<T>,
                                               private val hasErrorHandler: AtomicBoolean) : CancellablePromise<T>, InternalPromiseUtil.CompletablePromise<T> {
  constructor() : this(CompletableFuture(), AtomicBoolean())

  override fun isDone() = f.isDone

  override fun get() = nullizeCancelled { f.get() }

  override fun get(timeout: Long, unit: TimeUnit) = nullizeCancelled { f.get(timeout, unit) }

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

  override fun isCancelled() = f.isCancelled

  // because of the unorthodox contract: "double cancel must return false"
  override fun cancel(mayInterruptIfRunning: Boolean) = !isCancelled && f.cancel(mayInterruptIfRunning)

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
    return AsyncPromise(f.whenComplete { value, exception ->
      if (exception == null && !InternalPromiseUtil.isHandlerObsolete(handler)) {
        try {
          handler.accept(value)
        }
        catch (e: Throwable) {
          if (e !is ControlFlowException) {
            logger<AsyncPromise<*>>().error(e)
          }
        }
      }
    }, hasErrorHandler)
  }

  override fun onError(rejected: Consumer<Throwable>): Promise<T> {
    hasErrorHandler.set(true)
    return AsyncPromise(f.whenComplete { _, exception ->
      if (exception != null) {
        val toReport = if (exception is CompletionException && exception.cause != null) exception.cause!! else exception
        if (!InternalPromiseUtil.isHandlerObsolete(rejected)) {
          rejected.accept(toReport)
        }
      }
    }, hasErrorHandler)
  }

  override fun onProcessed(processed: Consumer<in T>): Promise<T> {
    hasErrorHandler.set(true)
    return AsyncPromise(f.whenComplete { value, _ ->
      if (!InternalPromiseUtil.isHandlerObsolete(processed)) {
        processed.accept(value)
      }
    }, hasErrorHandler)
  }

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
    return AsyncPromise(f.thenApply { done.`fun`(it) }, hasErrorHandler)
  }

  override fun <SUB_RESULT : Any?> thenAsync(doneF: Function<in T, Promise<SUB_RESULT>>): Promise<SUB_RESULT> {
    val convert: (T) -> CompletableFuture<SUB_RESULT> = {
      val promise = doneF.`fun`(it)
      val future = CompletableFuture<SUB_RESULT>()
      promise
        .onSuccess { value -> future.complete(value) }
        .onError { error -> future.completeExceptionally(error) }
      future
    }
    return AsyncPromise(f.thenCompose(convert), hasErrorHandler)
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
      logger<AsyncPromise<*>>().errorIfNotMessage(error)
    }
    return true
  }

  fun setError(error: String) = setError(createError(error))
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