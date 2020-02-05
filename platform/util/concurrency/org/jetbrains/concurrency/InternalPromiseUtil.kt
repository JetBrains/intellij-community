// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency

import com.intellij.util.Function
import com.intellij.util.ThreeState
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

internal val OBSOLETE_ERROR: RuntimeException = MessageError("Obsolete", false)

internal fun isHandlerObsolete(handler: Any): Boolean {
  return handler is Obsolescent && handler.isObsolete
}

internal interface PromiseImpl<T> {
  @Suppress("FunctionName")
  fun _setValue(value: PromiseValue<T>)
}

internal interface CompletablePromise<T> : Promise<T> {
  fun setResult(t: T?)
  fun setError(error: Throwable): Boolean
}

@ApiStatus.Internal
internal class MessageError(message: String, isLog: Boolean) : RuntimeException(message) {
  val log: ThreeState = ThreeState.fromBoolean(isLog)

  @Synchronized
  override fun fillInStackTrace() = this
}

@ApiStatus.Internal
fun isMessageError(exception: Exception): Boolean {
  return exception is MessageError
}

internal class PromiseValue<T> private constructor(val result: T?, val error: Throwable?) {
  companion object {
    @JvmStatic
    fun <T : Any?> createFulfilled(result: T?): PromiseValue<T> {
      return PromiseValue(result, null)
    }

    fun <T : Any?> createRejected(error: Throwable?): PromiseValue<T> {
      return PromiseValue(null, error)
    }
  }

  val state: Promise.State
    get() = if (error == null) Promise.State.SUCCEEDED else Promise.State.REJECTED

  val isCancelled: Boolean
    get() = error === OBSOLETE_ERROR

  fun getResultOrThrowError(): T? {
    return when {
      error == null -> result
      error === OBSOLETE_ERROR -> null
      else -> throw error.cause ?: error
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val value = other as PromiseValue<*>
    return result == value.result && error == value.error
  }

  override fun hashCode(): Int {
    return 31 * (result?.hashCode() ?: 0) + (error?.hashCode() ?: 0)
  }
}

internal class DonePromise<T>(private val value: PromiseValue<T>) : Promise<T>, Future<T>, PromiseImpl<T>, CancellablePromise<T> {
  /**
   * The same as @{link Future[Future.isDone]}.
   * Completion may be due to normal termination, an exception, or cancellation -- in all of these cases, this method will return true.
   */
  override fun isDone() = true

  override fun getState() = value.state

  override fun isCancelled() = this.value.isCancelled

  override fun get() = blockingGet(-1)

  override fun get(timeout: Long, unit: TimeUnit) = blockingGet(timeout.toInt(), unit)

  override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
    if (state == Promise.State.PENDING) {
      cancel()
      return true
    }
    else {
      return false
    }
  }

  override fun onSuccess(handler: Consumer<in T?>): CancellablePromise<T> {
    if (value.error != null) {
      return this
    }

    if (!isHandlerObsolete(handler)) {
      handler.accept(value.result)
    }
    return this
  }

  @Suppress("UNCHECKED_CAST")
  override fun processed(child: Promise<in T?>): Promise<T> {
    if (child is PromiseImpl<*>) {
      (child as PromiseImpl<T>)._setValue(value)
    }
    else if (child is CompletablePromise<*>) {
      (child as CompletablePromise<T>).setResult(value.result)
    }
    return this
  }

  override fun onProcessed(handler: Consumer<in T?>): CancellablePromise<T> {
    if (value.error == null) {
      onSuccess(handler)
    }
    else if (!isHandlerObsolete(handler)) {
      handler.accept(null)
    }
    return this
  }

  override fun onError(handler: Consumer<in Throwable?>): CancellablePromise<T> {
    if (value.error != null && !isHandlerObsolete(handler)) {
      handler.accept(value.error)
    }
    return this
  }

  override fun <SUB_RESULT : Any?> then(done: Function<in T, out SUB_RESULT>): Promise<SUB_RESULT> {
    @Suppress("UNCHECKED_CAST")
    return when {
      value.error != null -> this as Promise<SUB_RESULT>
      isHandlerObsolete(done) -> cancelledPromise()
      else -> DonePromise(PromiseValue.createFulfilled(done.`fun`(value.result)))
    }
  }

  override fun <SUB_RESULT : Any?> thenAsync(done: Function<in T, out Promise<SUB_RESULT>>): Promise<SUB_RESULT> {
    if (value.error == null) {
      return done.`fun`(value.result)
    }
    else {
      @Suppress("UNCHECKED_CAST")
      return this as Promise<SUB_RESULT>
    }
  }

  override fun blockingGet(timeout: Int, timeUnit: TimeUnit) = value.getResultOrThrowError()

  override fun _setValue(value: PromiseValue<T>) {}

  override fun cancel() {}
}