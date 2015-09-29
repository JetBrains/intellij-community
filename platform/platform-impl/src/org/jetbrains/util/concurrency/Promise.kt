/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.util.concurrency

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.concurrency.Promise as OJCPromise
import org.jetbrains.concurrency.AsyncPromise as OJCAsyncPromise

interface Promise<T> {
  enum class State {
    PENDING,
    FULFILLED,
    REJECTED
  }

  val state: State

  fun done(done: (T) -> Unit): Promise<T>

  fun rejected(rejected: (Throwable) -> Unit): Promise<T>

  fun processed(processed: (T?) -> Unit): Promise<T>

  fun <SUB_RESULT> then(done: (T) -> SUB_RESULT): Promise<SUB_RESULT>

  fun <SUB_RESULT> thenAsync(done: (T) -> Promise<SUB_RESULT>): Promise<SUB_RESULT>

  fun notify(child: AsyncPromise<T>): Promise<T>

  companion object {
    /**
     * Log error if not message error
     */
    fun logError(logger: Logger, e: Throwable) {
      if (e !is MessageError || ApplicationManager.getApplication().isUnitTestMode) {
        logger.error(e)
      }
    }

    fun all(promises: Collection<Promise<*>>): Promise<*> = all<Any?>(promises, null)

    fun <T> all(promises: Collection<Promise<*>>, totalResult: T): Promise<T> {
      if (promises.isEmpty()) {
        @Suppress("UNCHECKED_CAST")
        return DONE as Promise<T>
      }

      val totalPromise = AsyncPromise<T>()
      val done = CountDownConsumer(promises.size(), totalPromise, totalResult)
      val rejected = {it: Throwable ->
        if (totalPromise.state == Promise.State.PENDING) {
          totalPromise.setError(it)
        }
      }

      for (promise in promises) {
        //noinspection unchecked
        promise.done(done)
        promise.rejected(rejected)
      }
      return totalPromise
    }
  }
}

private val DONE: Promise<*> = DonePromise(null)
private val REJECTED: Promise<*> = RejectedPromise<Any>(MessageError("rejected"))

internal class MessageError(error: String) : RuntimeException(error) {
  @Synchronized fun fillInStackTrace(): Throwable? = this
}

fun <T> RejectedPromise(error: String): Promise<T> = RejectedPromise(MessageError(error))

fun ResolvedPromise(): Promise<*> = DONE

fun <T> ResolvedPromise(result: T): Promise<T> = DonePromise(result)

fun <T> OJCPromise<T>.toPromise(): AsyncPromise<T> {
  val promise = AsyncPromise<T>()
  val oldPromise = this
  done({ promise.setResult(it) })
    .rejected({ promise.setError(it) })

  if (oldPromise is OJCAsyncPromise) {
    promise
      .done { oldPromise.setResult(it) }
      .rejected { oldPromise.setError(it) }
  }
  return promise
}

fun <T> Promise<T>.toPromise(): OJCAsyncPromise<T> {
  val promise = OJCAsyncPromise<T>()
  done { promise.setResult(it) }
    .rejected { promise.setError(it) }
  return promise
}

private class CountDownConsumer<T>(private @field:Volatile var countDown: Int, private val promise: AsyncPromise<T>, private val totalResult: T) : (T) -> Unit {
  override fun invoke(p1: T) {
    if (--countDown == 0) {
      promise.setResult(totalResult)
    }
  }
}

val Promise<*>.isPending: Boolean
  get() = state == Promise.State.PENDING

val Promise<*>.isRejected: Boolean
  get() = state == Promise.State.REJECTED