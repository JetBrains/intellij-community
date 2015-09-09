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

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.concurrency.Obsolescent
import java.util.ArrayList

public class AsyncPromise<T> : Promise<T> {
  companion object {
    private val LOG = Logger.getInstance(javaClass<AsyncPromise<Any>>())

    public val OBSOLETE_ERROR: RuntimeException = MessageError("Obsolete")

    private fun <T> setHandler(oldConsumer: ((T) -> Unit)?, newConsumer: (T) -> Unit): (T) -> Unit {
      return when (oldConsumer) {
        null -> newConsumer
        is CompoundConsumer<*> -> {
          (oldConsumer as CompoundConsumer<T>).add(newConsumer)
          return oldConsumer
        }
        else -> CompoundConsumer(oldConsumer, newConsumer)
      }
    }

    private class CompoundConsumer<T>(c1: (T) -> Unit, c2: (T) -> Unit) : (T) -> Unit {
      private var consumers: MutableList<(T) -> Unit>? = ArrayList()

      init {
        synchronized (this) {
          consumers!!.add(c1)
          consumers!!.add(c2)
        }
      }

      override fun invoke(p1: T) {
        var list = synchronized (this) {
          var list = consumers!!
          consumers = null
          list
        }

        for (consumer in list) {
          if (!consumer.isObsolete()) {
            consumer(p1)
          }
        }
      }

      fun add(consumer: (T) -> Unit) {
        synchronized (this) {
          consumers?.add(consumer)
        }
      }
    }
  }

  private volatile var done: ((T) -> Unit)? = null
  private volatile var rejected: ((Throwable) -> Unit)? = null

  override volatile var state = Promise.State.PENDING
    private set

  // result object or error message
  private volatile var result: Any? = null

  override fun done(done: (T) -> Unit): Promise<T> {
    if (done.isObsolete()) {
      return this
    }

    when (state) {
      Promise.State.PENDING -> {
      }
      Promise.State.FULFILLED -> {
        @suppress("UNCHECKED_CAST")
        done(result as T)
        return this
      }
      Promise.State.REJECTED -> return this
    }

    this.done = setHandler(this.done, done)
    return this
  }

  override fun rejected(rejected: (Throwable) -> Unit): Promise<T> {
    if (rejected.isObsolete()) {
      return this
    }

    when (state) {
      Promise.State.PENDING -> {
      }
      Promise.State.FULFILLED -> return this
      Promise.State.REJECTED -> {
        rejected(result as Throwable)
        return this
      }
    }

    this.rejected = setHandler(this.rejected, rejected)
    return this
  }

  public fun get(): T {
    @suppress("UNCHECKED_CAST")
    return when (state) {
      Promise.State.FULFILLED -> result as T
      else -> null
    }
  }

  override fun <SUB_RESULT> then(done: (T) -> SUB_RESULT) = thenImpl<SUB_RESULT>(done, false)

  override fun <SUB_RESULT> thenAsync(done: (T) -> Promise<SUB_RESULT>): Promise<SUB_RESULT> = thenImpl(done, true)

  private fun <SUB_RESULT> thenImpl(done: (T) -> Any, asyncResult: Boolean): Promise<SUB_RESULT> {
    when (state) {
      Promise.State.PENDING -> {
      }
      Promise.State.FULFILLED -> {
        @suppress("UNCHECKED_CAST")
        return if (asyncResult) done(result as T) as Promise<SUB_RESULT> else DonePromise(done(result as T) as SUB_RESULT)
      }
      Promise.State.REJECTED -> return RejectedPromise(result as Throwable)
    }

    val promise = AsyncPromise<SUB_RESULT>()
    addHandlers({
      promise.catchError {
        if (done is Obsolescent && done.isObsolete()) {
          promise.setError(OBSOLETE_ERROR)
        }
        else {
          val subResult = done(it)
          if (asyncResult) {
            (subResult as Promise<*>).notify(promise)
            if (subResult is AsyncPromise<*> && subResult.state == Promise.State.PENDING) {
              // if promise fulfilled separately from sub result
              @suppress("UNCHECKED_CAST")
              promise.notify(subResult as AsyncPromise<SUB_RESULT>)
            }
          }
          else {
            @suppress("UNCHECKED_CAST")
            promise.setResult(subResult as SUB_RESULT)
          }
        }
      }
    },
      { promise.setError(it) }
    )
    return promise
  }

  override fun notify(child: AsyncPromise<T>): Promise<T> {
    if (child == this) {
      throw IllegalStateException("Child must no be equals to this")
    }

    when (state) {
      Promise.State.PENDING -> {
      }
      Promise.State.FULFILLED -> {
        @suppress("UNCHECKED_CAST")
        child.setResult(result as T)
        return this
      }
      Promise.State.REJECTED -> {
        child.setError(result as Throwable)
        return this
      }
    }

    addHandlers({ child.catchError { child.setResult(it) } }, { child.setError(it) })
    return this
  }

  private fun addHandlers(done: (T) -> Unit, rejected: (Throwable) -> Unit) {
    this.done = setHandler(this.done, done)
    this.rejected = setHandler(this.rejected, rejected)
  }

  public fun setResult(result: T) {
    if (state != Promise.State.PENDING) {
      return
    }

    state = Promise.State.FULFILLED
    this.result = result

    val done = this.done
    clearHandlers()
    if (done != null && !done.isObsolete()) {
      done(result)
    }
  }

  public fun setError(error: String): Boolean = setError(MessageError(error))

  public fun setError(error: Throwable): Boolean {
    if (state != Promise.State.PENDING) {
      return false
    }

    state = Promise.State.REJECTED
    result = error

    val rejected = this.rejected
    clearHandlers()
    if (rejected != null) {
      if (!rejected.isObsolete()) {
        rejected(error)
      }
    }
    else {
      Promise.logError(LOG, error)
    }
    return true
  }

  private fun clearHandlers() {
    done = null
    rejected = null
  }

  override fun processed(processed: (T) -> Unit): Promise<T> {
    done(processed)
    rejected { processed(null) }
    return this
  }
}

fun <T> ((T) -> Unit).isObsolete() = this is Obsolescent && this.isObsolete()

public inline fun AsyncPromise<out Any?>.catchError(task: () -> Unit) {
  try {
    task()
  }
  catch (e: Throwable) {
    setError(e)
  }
}