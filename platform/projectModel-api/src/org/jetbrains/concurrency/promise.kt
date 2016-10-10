/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import java.util.*

val Promise<*>.isRejected: Boolean
  get() = state == Promise.State.REJECTED

val Promise<*>.isPending: Boolean
  get() = state == Promise.State.PENDING

val Promise<*>.isFulfilled: Boolean
  get() = state == Promise.State.FULFILLED

internal val OBSOLETE_ERROR by lazy { createError("Obsolete") }

private val REJECTED: Promise<*> by lazy { RejectedPromise<Any?>(createError("rejected")) }
private val DONE: Promise<*> by lazy(LazyThreadSafetyMode.NONE) { Promise.DONE }
private val CANCELLED_PROMISE: Promise<*> by lazy { RejectedPromise<Any?>(OBSOLETE_ERROR) }

@Suppress("UNCHECKED_CAST")
fun <T> resolvedPromise(): Promise<T> = DONE as Promise<T>

fun nullPromise(): Promise<*> = DONE

fun <T> resolvedPromise(result: T): Promise<T> = if (result == null) resolvedPromise() else DonePromise(result)

@Suppress("UNCHECKED_CAST")
fun <T> rejectedPromise(): Promise<T> = REJECTED as Promise<T>

fun <T> rejectedPromise(error: String): Promise<T> = RejectedPromise(createError(error, true))

fun <T> rejectedPromise(error: Throwable?): Promise<T> = if (error == null) rejectedPromise() else RejectedPromise(error)

@Suppress("UNCHECKED_CAST")
fun <T> cancelledPromise(): Promise<T> = CANCELLED_PROMISE as Promise<T>


// only internal usage
interface ObsolescentFunction<Param, Result> : Function<Param, Result>, Obsolescent

abstract class ValueNodeAsyncFunction<PARAM, RESULT>(private val node: Obsolescent) : Function<PARAM, Promise<RESULT>>, Obsolescent {
  override fun isObsolete() = node.isObsolete
}

abstract class ObsolescentConsumer<T>(private val obsolescent: Obsolescent) : Obsolescent, Consumer<T> {
  override fun isObsolete() = obsolescent.isObsolete
}

inline fun <T, SUB_RESULT> Promise<T>.then(obsolescent: Obsolescent, crossinline handler: (T) -> SUB_RESULT) = then(object : ObsolescentFunction<T, SUB_RESULT> {
  override fun `fun`(param: T) = handler(param)

  override fun isObsolete() = obsolescent.isObsolete
})


inline fun <T> Promise<T>.done(node: Obsolescent, crossinline handler: (T) -> Unit) = done(object : ObsolescentConsumer<T>(node) {
  override fun consume(param: T) = handler(param)
})

@Suppress("UNCHECKED_CAST")
inline fun Promise<*>.processed(node: Obsolescent, crossinline handler: () -> Unit) = (this as Promise<Any?>).processed(object : ObsolescentConsumer<Any?>(node) {
  override fun consume(param: Any?) = handler()
})

@Suppress("UNCHECKED_CAST")
inline fun Promise<*>.doneRun(crossinline handler: () -> Unit) = done({ handler() })

@Suppress("UNCHECKED_CAST")
inline fun <T> Promise<*>.thenRun(crossinline handler: () -> T): Promise<T> = (this as Promise<Any?>).then({ handler() })

@Suppress("UNCHECKED_CAST")
inline fun Promise<*>.processedRun(crossinline handler: () -> Unit): Promise<*> = (this as Promise<Any?>).processed({ handler() })


inline fun <T, SUB_RESULT> Promise<T>.thenAsync(node: Obsolescent, crossinline handler: (T) -> Promise<SUB_RESULT>) = thenAsync(object : ValueNodeAsyncFunction<T, SUB_RESULT>(node) {
  override fun `fun`(param: T) = handler(param)
})

@Suppress("UNCHECKED_CAST")
inline fun <T> Promise<T>.thenAsyncAccept(node: Obsolescent, crossinline handler: (T) -> Promise<*>) = thenAsync(object : ValueNodeAsyncFunction<T, Any?>(node) {
  override fun `fun`(param: T) = handler(param) as Promise<Any?>
})

inline fun <T> Promise<T>.thenAsyncAccept(crossinline handler: (T) -> Promise<*>) = thenAsync(Function<T, Promise<Any?>> { param ->
  @Suppress("UNCHECKED_CAST")
  (return@Function handler(param) as Promise<Any?>)
})


inline fun Promise<*>.rejected(node: Obsolescent, crossinline handler: (Throwable) -> Unit) = rejected(object : ObsolescentConsumer<Throwable>(node) {
  override fun consume(param: Throwable) = handler(param)
})

fun <T> collectResults(promises: List<Promise<T>>): Promise<List<T>> {
  if (promises.isEmpty()) {
    return resolvedPromise(emptyList())
  }

  val results: MutableList<T> = if (promises.size == 1) SmartList<T>() else ArrayList<T>(promises.size)
  for (promise in promises) {
    promise.done { results.add(it) }
  }
  return all(promises, results)
}

@JvmOverloads
fun createError(error: String, log: Boolean = false): RuntimeException = MessageError(error, log)

inline fun <T> AsyncPromise<T>.compute(runnable: () -> T) {
  val result = catchError(runnable)
  if (!isRejected) {
    setResult(result)
  }
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

@SuppressWarnings("ExceptionClassNameDoesntEndWithException")
internal class MessageError(error: String, log: Boolean) : RuntimeException(error) {
  internal val log = ThreeState.fromBoolean(log)

  fun fillInStackTrace() = this
}

/**
 * Log error if not a message error
 */
fun Logger.errorIfNotMessage(e: Throwable): Boolean {
  if (e is MessageError) {
    val log = e.log
    if (log == ThreeState.YES || (log == ThreeState.UNSURE && (ApplicationManager.getApplication()?.isUnitTestMode ?: false))) {
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

fun ActionCallback.toPromise(): Promise<Void> {
  val promise = AsyncPromise<Void>()
  doWhenDone { promise.setResult(null) }.doWhenRejected { error -> promise.setError(createError(error ?: "Internal error")) }
  return promise
}

fun all(promises: Collection<Promise<*>>): Promise<*> = if (promises.size == 1) promises.first() else all(promises, null)

fun <T> all(promises: Collection<Promise<*>>, totalResult: T?): Promise<T> {
  if (promises.isEmpty()) {
    @Suppress("UNCHECKED_CAST")
    return DONE as Promise<T>
  }

  val totalPromise = AsyncPromise<T>()
  val done = CountDownConsumer(promises.size, totalPromise, totalResult)
  val rejected = Consumer<Throwable> { error -> totalPromise.setError(error) }

  for (promise in promises) {
    promise.done(done)
    promise.rejected(rejected)
  }
  return totalPromise
}

private class CountDownConsumer<T>(@Volatile private var countDown: Int, private val promise: AsyncPromise<T>, private val totalResult: T?) : Consumer<Any?> {
  override fun consume(t: Any?) {
    if (--countDown == 0) {
      promise.setResult(totalResult)
    }
  }
}

fun <T> any(promises: Collection<Promise<T>>, totalError: String): Promise<T> {
  if (promises.isEmpty()) {
    @Suppress("UNCHECKED_CAST")
    return DONE as Promise<T>
  }
  else if (promises.size == 1) {
    return promises.first()
  }

  val totalPromise = AsyncPromise<T>()
  val done = Consumer<T> { result -> totalPromise.setResult(result) }
  val rejected = object : Consumer<Throwable> {
    @Volatile private var toConsume = promises.size

    override fun consume(throwable: Throwable) {
      if (--toConsume <= 0) {
        totalPromise.setError(totalError)
      }
    }
  }

  for (promise in promises) {
    promise.done(done)
    promise.rejected(rejected)
  }
  return totalPromise
}