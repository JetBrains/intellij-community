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
package org.jetbrains.concurrency

import com.intellij.util.Consumer
import com.intellij.util.Function
import com.intellij.util.SmartList
import java.util.*

private val rejectedPromise = Promise.reject<Any?>("rejected")

// only internal usage
interface ObsolescentFunction<Param, Result> : Function<Param, Result>, Obsolescent

abstract class ValueNodeAsyncFunction<PARAM, RESULT>(private val node: Obsolescent) : AsyncFunction<PARAM, RESULT>, Obsolescent {
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
inline fun Promise<*>.doneRun(crossinline handler: () -> Unit) = (this as Promise<Any?>).done { handler() }

@Suppress("UNCHECKED_CAST")
inline fun <T> Promise<*>.thenRun(crossinline handler: () -> T): Promise<T> = (this as Promise<Any?>).then { handler() }

@Suppress("UNCHECKED_CAST")
inline fun Promise<*>.processedRun(crossinline handler: () -> Unit): Promise<*> = (this as Promise<Any?>).processed { handler() }


inline fun <T, SUB_RESULT> Promise<T>.thenAsync(node: Obsolescent, crossinline handler: (T) -> Promise<SUB_RESULT>) = thenAsync(object : ValueNodeAsyncFunction<T, SUB_RESULT>(node) {
  override fun `fun`(param: T) = handler(param)
})

@Suppress("UNCHECKED_CAST")
inline fun <T> Promise<T>.thenAsyncAccept(node: Obsolescent, crossinline handler: (T) -> Promise<*>) = thenAsync(object : ValueNodeAsyncFunction<T, Any?>(node) {
  override fun `fun`(param: T) = handler(param) as Promise<Any?>
})

inline fun <T> Promise<T>.thenAsyncAccept(crossinline handler: (T) -> Promise<*>) = thenAsync(AsyncFunction<T, kotlin.Any?> { param ->
  @Suppress("UNCHECKED_CAST")
  (return@AsyncFunction handler(param) as Promise<Any?>)
})


inline fun Promise<*>.rejected(node: Obsolescent, crossinline handler: (Throwable) -> Unit) = rejected(object : ObsolescentConsumer<Throwable>(node) {
  override fun consume(param: Throwable) = handler(param)
})


fun resolvedPromise(): Promise<*> = Promise.DONE

fun <T> resolvedPromise(result: T) = Promise.resolve(result)

fun <T> rejectedPromise(error: String): Promise<T> = Promise.reject(error)

fun <T> rejectedPromise(error: Throwable): Promise<T> = Promise.reject(error)

@Suppress("CAST_NEVER_SUCCEEDS")
fun <T> rejectedPromise(): Promise<T> = rejectedPromise as Promise<T>

val Promise<*>.isRejected: Boolean
  get() = state == Promise.State.REJECTED

val Promise<*>.isPending: Boolean
  get() = state == Promise.State.PENDING

val Promise<*>.isFulfilled: Boolean
  get() = state == Promise.State.FULFILLED

inline fun <T> AsyncPromise<*>.catchError(runnable: () -> T): T? {
  try {
    return runnable()
  }
  catch (e: Throwable) {
    setError(e)
    return null
  }
}

fun <T> collectResults(promises: List<Promise<T>>): Promise<List<T>> {
  if (promises.isEmpty()) {
    return resolvedPromise(emptyList())
  }

  val results: MutableList<T> = if (promises.size == 1) SmartList<T>() else ArrayList<T>(promises.size)
  for (promise in promises) {
    promise.done { results.add(it) }
  }
  return Promise.all(promises, results)
}

fun createError(error: String, log: Boolean): RuntimeException = Promise.MessageError(error, log)

inline fun <T> AsyncPromise<T>.compute(runnable: () -> T) {
  val result = catchError(runnable)
  if (!isRejected) {
    setResult(result)
  }
}