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

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Getter
import com.intellij.util.Consumer
import com.intellij.util.Function
import java.util.*
import java.util.concurrent.atomic.AtomicReference

private val LOG = Logger.getInstance(AsyncPromise::class.java)

@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
private val OBSOLETE_ERROR = Promise.createError("Obsolete")

open class AsyncPromise<T> : Promise<T>(), Getter<T> {
  private val doneRef = AtomicReference<Consumer<in T>?>()
  private val rejectedRef = AtomicReference<Consumer<in Throwable>?>()

  private val state = AtomicReference(State.PENDING)

  // result object or error message
  @Volatile private var result: Any? = null

  override fun getState() = state.get()!!

  override fun done(done: Consumer<in T>): Promise<T> {
    if (isObsolete(done)) {
      return this
    }

    when (state.get()!!) {
      State.PENDING -> {
        setHandler(doneRef, done, State.FULFILLED)
      }
      State.FULFILLED -> {
        @Suppress("UNCHECKED_CAST")
        done.consume(result as T?)
      }
      State.REJECTED -> {
      }
    }

    return this
  }

  override fun rejected(rejected: Consumer<Throwable>): Promise<T> {
    if (isObsolete(rejected)) {
      return this
    }

    when (state.get()!!) {
      State.PENDING -> {
        setHandler(rejectedRef, rejected, State.REJECTED)
      }
      State.FULFILLED -> {
      }
      State.REJECTED -> {
        rejected.consume(result as Throwable?)
      }
    }

    return this
  }

  @Suppress("UNCHECKED_CAST")
  override fun get() = if (state.get() == State.FULFILLED) result as T? else null

  override fun <SUB_RESULT> then(fulfilled: Function<in T, out SUB_RESULT>): Promise<SUB_RESULT> {
    @Suppress("UNCHECKED_CAST")
    when (state.get()!!) {
      State.PENDING -> {
      }
      State.FULFILLED -> return DonePromise<SUB_RESULT>(
          fulfilled.`fun`(result as T?))
      State.REJECTED -> return rejectedPromise(result as Throwable)
    }

    val promise = AsyncPromise<SUB_RESULT>()
    addHandlers(Consumer({ result ->
                           promise.catchError {
                             if (fulfilled is Obsolescent && fulfilled.isObsolete) {
                               promise.cancel()
                             }
                             else {
                               promise.setResult(fulfilled.`fun`(result))
                             }
                           }
                         }), Consumer({ promise.setError(it) }))
    return promise
  }

  override fun notify(child: AsyncPromise<in T>) {
    LOG.assertTrue(child !== this)

    when (state.get()!!) {
      State.PENDING -> {
        addHandlers(Consumer({ child.catchError { child.setResult(it) } }), Consumer({ child.setError(it) }))
      }
      State.FULFILLED -> {
        @Suppress("UNCHECKED_CAST")
        child.setResult(result as T)
      }
      State.REJECTED -> {
        child.setError((result as Throwable?)!!)
      }
    }
  }

  override fun <SUB_RESULT> thenAsync(fulfilled: Function<in T, Promise<SUB_RESULT>>): Promise<SUB_RESULT> {
    @Suppress("UNCHECKED_CAST")
    when (state.get()!!) {
      State.PENDING -> {
      }
      State.FULFILLED -> return fulfilled.`fun`(result as T?)
      State.REJECTED -> return rejectedPromise(result as Throwable)
    }

    val promise = AsyncPromise<SUB_RESULT>()
    val rejectedHandler = Consumer<Throwable>({ promise.setError(it) })
    addHandlers(Consumer({
                           promise.catchError {
                             fulfilled.`fun`(it)
                                 .done { promise.catchError { promise.setResult(it) } }
                                 .rejected(rejectedHandler)
                           }
                         }), rejectedHandler)
    return promise
  }

  override fun processed(fulfilled: AsyncPromise<in T>): Promise<T> {
    when (state.get()!!) {
      State.PENDING -> {
        addHandlers(Consumer({ result -> fulfilled.catchError { fulfilled.setResult(result) } }), Consumer({ fulfilled.setError(it) }))
      }
      State.FULFILLED -> {
        @Suppress("UNCHECKED_CAST")
        fulfilled.setResult(result as T)
      }
      State.REJECTED -> {
        fulfilled.setError((result as Throwable?)!!)
      }
    }
    return this
  }

  private fun addHandlers(done: Consumer<T>, rejected: Consumer<Throwable>) {
    setHandler(doneRef, done, State.FULFILLED)
    setHandler(rejectedRef, rejected, State.REJECTED)
  }

  fun setResult(result: T?) {
    if (!state.compareAndSet(State.PENDING, State.FULFILLED)) {
      return
    }

    this.result = result

    val done = getAndClearHandler(doneRef)
    rejectedRef.set(null)

    if (done != null && !isObsolete(done)) {
      done.consume(result)
    }
  }

  fun setError(error: String) = setError(Promise.createError(error))

  fun cancel() {
    setError(OBSOLETE_ERROR)
  }

  open fun setError(error: Throwable): Boolean {
    if (!state.compareAndSet(State.PENDING, State.REJECTED)) {
      return false
    }

    result = error

    val rejected = getAndClearHandler(rejectedRef)
    doneRef.set(null)

    if (rejected == null) {
      Promise.logError(LOG, error)
    }
    else if (!isObsolete(rejected)) {
      rejected.consume(error)
    }
    return true
  }

  private fun <T> getAndClearHandler(ref: AtomicReference<Consumer<in T>?>): Consumer<in T>? {
    var handler: Consumer<in T>?
    do {
      handler = ref.get()
    }
    while (!ref.compareAndSet(handler, null))
    return handler
  }

  override fun processed(processed: Consumer<in T>): Promise<T> {
    done(processed)
    rejected { processed.consume(null) }
    return this
  }

  private fun <T> setHandler(ref: AtomicReference<Consumer<in T>?>, newConsumer: Consumer<in T>, targetState: State) {
    while (true) {
      val oldConsumer = ref.get()
      val newEffectiveConsumer = when (oldConsumer) {
        null -> newConsumer
        is CompoundConsumer<*> -> {
          @Suppress("UNCHECKED_CAST")
          val compoundConsumer = oldConsumer as CompoundConsumer<T>
          var executed = true
          synchronized(compoundConsumer) {
            compoundConsumer.consumers?.let {
              it.add(newConsumer)
              executed = false
            }
          }

          // clearHandlers was called - just execute newConsumer
          if (executed) {
            if (state.get() == targetState) {
              @Suppress("UNCHECKED_CAST")
              newConsumer.consume(result as T?)
            }
            return
          }

          compoundConsumer
        }
        else -> CompoundConsumer(oldConsumer, newConsumer)
      }

      if (ref.compareAndSet(oldConsumer, newEffectiveConsumer)) {
        break
      }
    }

    if (state.get() == targetState) {
      getAndClearHandler(ref)?.let {
        @Suppress("UNCHECKED_CAST")
        it.consume(result as T?)
      }
    }
  }
}

private class CompoundConsumer<T>(c1: Consumer<in T>, c2: Consumer<in T>) : Consumer<T> {
  var consumers: MutableList<Consumer<in T>>? = ArrayList()

  init {
    synchronized(this) {
      consumers!!.add(c1)
      consumers!!.add(c2)
    }
  }

  override fun consume(t: T) {
    val list = synchronized(this) {
      val list = consumers
      consumers = null
      list
    } ?: return

    for (consumer in list) {
      if (!isObsolete(consumer)) {
        consumer.consume(t)
      }
    }
  }

  fun add(consumer: Consumer<in T>) {
    synchronized(this) {
      consumers.let {
        if (it == null) {
          // it means that clearHandlers was called
        }
        consumers?.add(consumer)
      }
    }
  }
}

internal fun isObsolete(consumer: Consumer<*>?) = consumer is Obsolescent && consumer.isObsolete

inline fun <T> AsyncPromise<*>.catchError(runnable: () -> T): T? {
  try {
    return runnable()
  }
  catch (e: Throwable) {
    setError(e)
    return null
  }
}

private val cancelledPromise = RejectedPromise<Any?>(OBSOLETE_ERROR)

@Suppress("UNCHECKED_CAST")
fun <T> cancelledPromise(): Promise<T> = cancelledPromise as Promise<T>

fun <T> rejectedPromise(error: Throwable): Promise<T> = Promise.reject(error)