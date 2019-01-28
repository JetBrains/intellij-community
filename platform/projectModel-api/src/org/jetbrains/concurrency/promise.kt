// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("Promises")
package org.jetbrains.concurrency

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.ActionCallback
import com.intellij.util.Function
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.toMutableSmartList
import org.jetbrains.concurrency.InternalPromiseUtil.MessageError
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

val Promise<*>.isRejected: Boolean
  get() = state == Promise.State.REJECTED

val Promise<*>.isPending: Boolean
  get() = state == Promise.State.PENDING

private val REJECTED: Promise<*> by lazy { DonePromise<Any?>(InternalPromiseUtil.PromiseValue.createRejected(createError("rejected"))) }

@Suppress("UNCHECKED_CAST")
fun <T> resolvedPromise(): Promise<T> = InternalPromiseUtil.FULFILLED_PROMISE.value as Promise<T>

fun nullPromise(): Promise<*> = InternalPromiseUtil.FULFILLED_PROMISE.value

/**
 * Creates a promise that is resolved with the given value.
 */
fun <T> resolvedPromise(result: T): Promise<T> = resolvedCancellablePromise(result)

fun <T> resolvedCancellablePromise(result: T): CancellablePromise<T> {
  @Suppress("UNCHECKED_CAST")
  return when (result) {
    null -> InternalPromiseUtil.FULFILLED_PROMISE.value as CancellablePromise<T>
    else -> DonePromise(InternalPromiseUtil.PromiseValue.createFulfilled(result))
  }
}

@Suppress("UNCHECKED_CAST")
/**
 * Consider to pass error.
 */
fun <T> rejectedPromise(): Promise<T> = REJECTED as Promise<T>

fun <T> rejectedPromise(error: String): Promise<T> = DonePromise(InternalPromiseUtil.PromiseValue.createRejected(createError(error, true)))

fun <T> rejectedPromise(error: Throwable?): Promise<T> {
  @Suppress("UNCHECKED_CAST")
  return when (error) {
    null -> REJECTED as Promise<T>
    else -> DonePromise(InternalPromiseUtil.PromiseValue.createRejected(error))
  }
}

@Suppress("UNCHECKED_CAST")
fun <T> cancelledPromise(): Promise<T> = InternalPromiseUtil.CANCELLED_PROMISE.value as Promise<T>


// only internal usage
interface ObsolescentFunction<Param, Result> : Function<Param, Result>, Obsolescent

abstract class ValueNodeAsyncFunction<PARAM, RESULT>(private val node: Obsolescent) : Function<PARAM, Promise<RESULT>>, Obsolescent {
  override fun isObsolete(): Boolean = node.isObsolete
}

abstract class ObsolescentConsumer<T>(private val obsolescent: Obsolescent) : Obsolescent, Consumer<T> {
  override fun isObsolete(): Boolean = obsolescent.isObsolete
}

inline fun <T, SUB_RESULT> Promise<T>.then(obsolescent: Obsolescent, crossinline handler: (T) -> SUB_RESULT): Promise<SUB_RESULT> = then(object : ObsolescentFunction<T, SUB_RESULT> {
  override fun `fun`(param: T) = handler(param)

  override fun isObsolete() = obsolescent.isObsolete
})


inline fun <T> Promise<T>.onSuccess(node: Obsolescent, crossinline handler: (T) -> Unit): Promise<T> = onSuccess(object : ObsolescentConsumer<T>(node) {
  override fun accept(param: T) = handler(param)
})

inline fun Promise<*>.processed(node: Obsolescent, crossinline handler: () -> Unit): Promise<Any?>? {
  @Suppress("UNCHECKED_CAST")
  return (this as Promise<Any?>)
    .onProcessed(object : ObsolescentConsumer<Any?>(node) {
      override fun accept(param: Any?) = handler()
    })
}

@Suppress("UNCHECKED_CAST")
inline fun <T> Promise<*>.thenRun(crossinline handler: () -> T): Promise<T> = (this as Promise<Any?>).then { handler() }

@Suppress("UNCHECKED_CAST")
inline fun Promise<*>.processedRun(crossinline handler: () -> Unit): Promise<*> {
  return (this as Promise<Any?>).onProcessed { handler() }
}


inline fun <T, SUB_RESULT> Promise<T>.thenAsync(node: Obsolescent, crossinline handler: (T) -> Promise<SUB_RESULT>): Promise<SUB_RESULT> = thenAsync(object : ValueNodeAsyncFunction<T, SUB_RESULT>(node) {
  override fun `fun`(param: T) = handler(param)
})

@Suppress("UNCHECKED_CAST")
inline fun <T> Promise<T>.thenAsyncAccept(node: Obsolescent, crossinline handler: (T) -> Promise<*>): Promise<Any?> {
  return thenAsync(object : ValueNodeAsyncFunction<T, Any?>(node) {
    override fun `fun`(param: T) = handler(param) as Promise<Any?>
  })
}

inline fun <T> Promise<T>.thenAsyncAccept(crossinline handler: (T) -> Promise<*>): Promise<Any?> = thenAsync(Function<T, Promise<Any?>> { param ->
  @Suppress("UNCHECKED_CAST")
  (return@Function handler(param) as Promise<Any?>)
})


inline fun Promise<*>.onError(node: Obsolescent, crossinline handler: (Throwable) -> Unit): Promise<out Any> = onError(object : ObsolescentConsumer<Throwable>(node) {
  override fun accept(param: Throwable) = handler(param)
})

/**
 * Merge results into one list. Results are ordered as in the promises list.
 *
 * `T` here is a not nullable type, if you use this method from Java, take care that all promises are not resolved to `null`.
 *
 * If `ignoreErrors = false`, list of the same size is returned.
 * If `ignoreErrors = true`, list of different size is returned if some promise failed with error.
 */
@JvmOverloads
fun <T : Any> Collection<Promise<T>>.collectResults(ignoreErrors: Boolean = false): Promise<List<T>> {
  if (isEmpty()) {
    return resolvedPromise(emptyList())
  }

  val result = AsyncPromise<List<T>>()
  val latch = AtomicInteger(size)
  val list = Collections.synchronizedList(Collections.nCopies<T?>(size, null).toMutableSmartList())

  fun arrive() {
    if (latch.decrementAndGet() == 0) {
      if (ignoreErrors) {
        list.removeIf { it == null }
      }
      @Suppress("UNCHECKED_CAST")
      result.setResult(list as List<T>)
    }
  }

  for ((i, promise) in this.withIndex()) {
    promise.onSuccess {
      list.set(i, it)
      arrive()
    }
    promise.onError {
      if (ignoreErrors) {
        arrive()
      }
      else {
        result.setError(it)
      }
    }
  }
  return result
}

@JvmOverloads
fun createError(error: String, log: Boolean = false): RuntimeException = MessageError(error, log)

inline fun <T> AsyncPromise<T>.compute(runnable: () -> T) {
  val result = try {
    runnable()
  }
  catch (e: Throwable) {
    setError(e)
    return
  }

  setResult(result)
}

inline fun <T> runAsync(crossinline runnable: () -> T): Promise<T> {
  val promise = AsyncPromise<T>()
  AppExecutorUtil.getAppExecutorService().execute {
    val result = try {
      runnable()
    }
    catch (e: Throwable) {
      promise.setError(e)
      return@execute
    }
    promise.setResult(result)
  }
  return promise
}

/**
 * Log error if not a message error
 */
fun Logger.errorIfNotMessage(e: Throwable): Boolean {
  if (e is MessageError) {
    val log = e.log
    if (log == ThreeState.YES || (log == ThreeState.UNSURE && ApplicationManager.getApplication()?.isUnitTestMode == true)) {
      error(e)
      return true
    }
  }
  else if (e !is ControlFlowException && e !is CancellationException) {
    error(e)
    return true
  }

  return false
}

fun ActionCallback.toPromise(): Promise<Any?> {
  val promise = AsyncPromise<Any?>()
  doWhenDone { promise.setResult(null) }
    .doWhenRejected { error -> promise.setError(createError(error ?: "Internal error")) }
  return promise
}

fun Promise<Any?>.toActionCallback(): ActionCallback {
  val result = ActionCallback()
  onSuccess { result.setDone() }
  onError { result.setRejected() }
  return result
}

fun Collection<Promise<*>>.all(): Promise<*> = if (size == 1) first() else all(null)

/**
 * @see collectResults
 */
@JvmOverloads
fun <T: Any?> Collection<Promise<*>>.all(totalResult: T, ignoreErrors: Boolean = false): Promise<T> {
  if (isEmpty()) {
    return resolvedPromise()
  }

  val totalPromise = AsyncPromise<T>()
  val done = CountDownConsumer(size, totalPromise, totalResult)
  val rejected = if (ignoreErrors) {
    Consumer { done.accept(null) }
  }
  else {
    Consumer<Throwable> { totalPromise.setError(it) }
  }

  for (promise in this) {
    promise.onSuccess(done)
    promise.onError(rejected)
  }
  return totalPromise
}

private class CountDownConsumer<T : Any?>(countDown: Int, private val promise: AsyncPromise<T>, private val totalResult: T) : Consumer<Any?> {
  private val countDown = AtomicInteger(countDown)

  override fun accept(t: Any?) {
    if (countDown.decrementAndGet() == 0) {
      promise.setResult(totalResult)
    }
  }
}

fun <T> any(promises: Collection<Promise<T>>, totalError: String): Promise<T> {
  if (promises.isEmpty()) {
    return resolvedPromise()
  }
  else if (promises.size == 1) {
    return promises.first()
  }

  val totalPromise = AsyncPromise<T>()
  val done = Consumer<T> { result -> totalPromise.setResult(result) }
  val rejected = object : Consumer<Throwable> {
    private val toConsume = AtomicInteger(promises.size)

    override fun accept(throwable: Throwable) {
      if (toConsume.decrementAndGet() <= 0) {
        totalPromise.setError(totalError)
      }
    }
  }

  for (promise in promises) {
    promise.onSuccess(done)
    promise.onError(rejected)
  }
  return totalPromise
}