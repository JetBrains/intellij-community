// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.concurrency

import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.ExceptionUtilRt
import com.intellij.util.Function
import com.intellij.util.concurrency.captureBiConsumerThreadContext
import com.intellij.util.concurrency.createChildContext
import kotlinx.coroutines.DelicateCoroutinesApi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.concurrency.Promise.State
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiConsumer
import java.util.function.Consumer
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * **Obsolescence notice**
 *
 * Please use [Kotlin coroutines](https://plugins.jetbrains.com/docs/intellij/kotlin-coroutines.html)
 * Instead of this class use [kotlinx.coroutines.CompletableDeferred]
 */
@ApiStatus.Obsolete
open class AsyncPromise<T> private constructor(
  internal val f: CompletableFuture<T>,
  private val hasErrorHandler: AtomicBoolean,
  addExceptionHandler: Boolean,
) : CancellablePromise<T>, CompletablePromise<T> {
  companion object {
    private val LOG = Logger.getInstance(AsyncPromise::class.java)

    @Internal
    @JvmField
    val CANCELED: CancellationException = object : CancellationException() {
      override fun fillInStackTrace(): Throwable = this
    }
  }

  constructor() : this(CompletableFuture(), AtomicBoolean(), addExceptionHandler = false)

  init {
    if (addExceptionHandler) {
      f.handle { value, error ->
        if (error != null && shouldLogErrors()) {
          LOG.errorIfNotMessage((error as? CompletionException)?.cause ?: error)
        }
        value
      }
    }
  }

  override fun isDone(): Boolean = f.isDone

  override fun get(): T? = nullizeCancelled { f.get() }

  override fun get(timeout: Long, unit: TimeUnit): T? = nullizeCancelled { f.get(timeout, unit) }

  // because of the contract: get() should return null for canceled promise
  private inline fun nullizeCancelled(value: () -> T?): T? {
    return try {
      value()
    }
    catch (pce: ProcessCanceledException) {
      throw pce
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

  override fun onSuccess(handler: Consumer<in T>): AsyncPromise<T> {
    return AsyncPromise(whenComplete { value, error ->
      if (error == null && !isHandlerObsolete(handler)) {
        handler.accept(value)
      }
    }, hasErrorHandler, addExceptionHandler = true)
  }

  override fun onError(rejected: Consumer<in Throwable>): AsyncPromise<T> {
    hasErrorHandler.set(true)
    return AsyncPromise(whenComplete { _, error ->
      if (error != null && !isHandlerObsolete(rejected)) {
        rejected.accept((error as? CompletionException)?.cause ?: error)
      }
    }, hasErrorHandler, addExceptionHandler = false)
  }

  override fun onProcessed(processed: Consumer<in T?>): AsyncPromise<T> {
    return AsyncPromise(whenComplete { value, _ ->
      if (!isHandlerObsolete(processed)) {
        processed.accept(value)
      }
    }, hasErrorHandler, addExceptionHandler = true)
  }

  private fun whenComplete(action: BiConsumer<T, Throwable?>): CompletableFuture<T> {
    val result = CompletableFuture<T>()
    val captured = captureBiConsumerThreadContext(action)
    f.handle { value, error ->
      try {
        captured.accept(value, error)
        if (error != null) result.completeExceptionally(error)
        else result.complete(value)
      }
      catch (e: Throwable) {
        if (error != null) e.addSuppressed(error)
        result.completeExceptionally(e)
      }
      value
    }
    return result
  }

  @Throws(TimeoutException::class)
  override fun blockingGet(timeout: Int, timeUnit: TimeUnit): T? {
    try {
      return get(timeout.toLong(), timeUnit)
    }
    catch (e: ExecutionException) {
      val cause = e.cause ?: throw e
      // checked exceptions must be not thrown as is - API should conform to Java standards
      ExceptionUtilRt.rethrowUnchecked(cause)
      throw e
    }
  }

  override fun <SUB_RESULT : Any?> then(done: Function<in T, out SUB_RESULT>): Promise<SUB_RESULT> {
    return AsyncPromise(wrapWithCancellationPropagation { ctx ->
      f.thenApply { t ->
        installThreadContext(ctx, true) {
          done.`fun`(t)
        }
      }
    }, hasErrorHandler, addExceptionHandler = true)
  }

  @OptIn(DelicateCoroutinesApi::class)
  private fun <T> wrapWithCancellationPropagation(producer: (CoroutineContext) -> CompletableFuture<T>): CompletableFuture<T> {
    val childContext = createChildContext("AsyncPromise: $this, $producer")
    val capturingFuture = producer(childContext.context)
    val ijElementsToken = childContext.applyContextActions(false)
    return capturingFuture.whenComplete { _, result ->
      ijElementsToken.finish()
      val continuation = childContext.continuation
      when (result) {
        null -> continuation?.resume(Unit)
        is ProcessCanceledException -> continuation?.resumeWithException(CancellationException())
        is CompletionException -> when (val cause = result.cause) {
          is CancellationException -> continuation?.resumeWithException(cause)
          null -> continuation?.resume(Unit)
          else -> continuation?.resumeWithException(cause)
        }
        else -> continuation?.resumeWithException(result)
      }
    }
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

    if (shouldLogErrors()) {
      LOG.errorIfNotMessage(error)
    }
    return true
  }

  protected open fun shouldLogErrors(): Boolean = !hasErrorHandler.get()

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
