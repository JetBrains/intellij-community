// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("Promises")
package org.jetbrains.concurrency

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.ActionCallback
import com.intellij.util.Consumer
import com.intellij.util.Function
import com.intellij.util.SmartList
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.concurrency.InternalPromiseUtil.MessageError
import java.util.*

val Promise<*>.isRejected: Boolean
  get() = state == Promise.State.REJECTED

val Promise<*>.isPending: Boolean
  get() = state == Promise.State.PENDING

val Promise<*>.isFulfilled: Boolean
  get() = state == Promise.State.FULFILLED

private val REJECTED: Promise<*> by lazy { DonePromise<Any?>(InternalPromiseUtil.PromiseValue.createRejected(createError("rejected"))) }

@Suppress("UNCHECKED_CAST")
fun <T> resolvedPromise(): Promise<T> = InternalPromiseUtil.FULFILLED_PROMISE.value as Promise<T>

fun nullPromise(): Promise<*> = InternalPromiseUtil.FULFILLED_PROMISE.value

/**
 * Creates a promise that is resolved with the given value.
 */
fun <T> resolvedPromise(result: T): Promise<T> = Promise.resolve(result)

@Suppress("UNCHECKED_CAST")
fun <T> rejectedPromise(): Promise<T> = REJECTED as Promise<T>

fun <T> rejectedPromise(error: String): Promise<T> = DonePromise(InternalPromiseUtil.PromiseValue.createRejected(createError(error, true)))

fun <T> rejectedPromise(error: Throwable?): Promise<T> {
  return when (error) {
    null -> rejectedPromise()
    else -> DonePromise(InternalPromiseUtil.PromiseValue.createRejected(error))
  }
}

@Suppress("UNCHECKED_CAST")
fun <T> cancelledPromise(): Promise<T> = InternalPromiseUtil.CANCELLED_PROMISE.value as Promise<T>


// only internal usage
interface ObsolescentFunction<Param, Result> : Function<Param, Result>, Obsolescent

abstract class ValueNodeAsyncFunction<PARAM, RESULT>(private val node: Obsolescent) : Function<PARAM, Promise<RESULT>>, Obsolescent {
  override fun isObsolete() = node.isObsolete
}

abstract class ObsolescentConsumer<T>(private val obsolescent: Obsolescent) : Obsolescent, java.util.function.Consumer<T> {
  override fun isObsolete() = obsolescent.isObsolete
}

inline fun <T, SUB_RESULT> Promise<T>.then(obsolescent: Obsolescent, crossinline handler: (T) -> SUB_RESULT) = then(object : ObsolescentFunction<T, SUB_RESULT> {
  override fun `fun`(param: T) = handler(param)

  override fun isObsolete() = obsolescent.isObsolete
})


inline fun <T> Promise<T>.onSuccess(node: Obsolescent, crossinline handler: (T) -> Unit) = onSuccess(object : ObsolescentConsumer<T>(node) {
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
inline fun Promise<*>.doneRun(crossinline handler: () -> Unit) = onSuccess { handler() }

@Suppress("UNCHECKED_CAST")
inline fun <T> Promise<*>.thenRun(crossinline handler: () -> T): Promise<T> = (this as Promise<Any?>).then({ handler() })

@Suppress("UNCHECKED_CAST")
inline fun Promise<*>.processedRun(crossinline handler: () -> Unit): Promise<*> {
  return (this as Promise<Any?>).onProcessed({ handler() })
}


inline fun <T, SUB_RESULT> Promise<T>.thenAsync(node: Obsolescent, crossinline handler: (T) -> Promise<SUB_RESULT>) = thenAsync(object : ValueNodeAsyncFunction<T, SUB_RESULT>(node) {
  override fun `fun`(param: T) = handler(param)
})

@Suppress("UNCHECKED_CAST")
inline fun <T> Promise<T>.thenAsyncAccept(node: Obsolescent, crossinline handler: (T) -> Promise<*>): Promise<Any?> {
  return thenAsync(object : ValueNodeAsyncFunction<T, Any?>(node) {
    override fun `fun`(param: T) = handler(param) as Promise<Any?>
  })
}

inline fun <T> Promise<T>.thenAsyncAccept(crossinline handler: (T) -> Promise<*>) = thenAsync(Function<T, Promise<Any?>> { param ->
  @Suppress("UNCHECKED_CAST")
  (return@Function handler(param) as Promise<Any?>)
})


inline fun Promise<*>.onError(node: Obsolescent, crossinline handler: (Throwable) -> Unit) = onError(object : ObsolescentConsumer<Throwable>(node) {
  override fun accept(param: Throwable) = handler(param)
})

@JvmOverloads
fun <T> collectResults(promises: List<Promise<T>>, ignoreErrors: Boolean = false): Promise<List<T>> {
  if (promises.isEmpty()) {
    return resolvedPromise(emptyList())
  }

  val results: MutableList<T> = if (promises.size == 1) SmartList<T>() else ArrayList(promises.size)
  for (promise in promises) {
    promise.onSuccess { results.add(it) }
  }
  return all(promises, results, ignoreErrors)
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
  else if (e !is ProcessCanceledException) {
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

fun all(promises: Collection<Promise<*>>): Promise<*> = if (promises.size == 1) promises.first() else all(promises, null)

@JvmOverloads
fun <T: Any?> all(promises: Collection<Promise<*>>, totalResult: T, ignoreErrors: Boolean = false): Promise<T> {
  if (promises.isEmpty()) {
    return resolvedPromise()
  }

  val totalPromise = AsyncPromise<T>()
  val done = CountDownConsumer(promises.size, totalPromise, totalResult)
  val rejected = if (ignoreErrors) {
    Consumer { done.accept(null) }
  }
  else {
    Consumer<Throwable> { totalPromise.setError(it) }
  }

  for (promise in promises) {
    promise.onSuccess(done)
    promise.rejected(rejected)
  }
  return totalPromise
}

private class CountDownConsumer<T : Any?>(@Volatile private var countDown: Int, private val promise: AsyncPromise<T>, private val totalResult: T) : java.util.function.Consumer<Any?> {
  override fun accept(t: Any?) {
    if (--countDown == 0) {
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
  val done = java.util.function.Consumer<T> { result -> totalPromise.setResult(result) }
  val rejected = object : java.util.function.Consumer<Throwable> {
    @Volatile private var toConsume = promises.size

    override fun accept(throwable: Throwable) {
      if (--toConsume <= 0) {
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