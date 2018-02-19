// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.concurrency

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Getter
import com.intellij.util.Consumer
import com.intellij.util.Function
import org.jetbrains.concurrency.Promise.State
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

private val LOG = Logger.getInstance(AsyncPromise::class.java)

private data class PromiseValue<out T>(val result: T? = null, val error: Throwable? = null)

open class AsyncPromise<T> : Promise<T>, Getter<T>, CancellablePromise<T> {
  private val doneRef = AtomicReference<Consumer<in T>?>()
  private val rejectedRef = AtomicReference<Consumer<in Throwable>?>()

  private val valueRef = AtomicReference<PromiseValue<T>?>(null)

  override fun getState(): State {
    val value = valueRef.get()
    return when {
      value == null -> State.PENDING
      value.error == null -> State.FULFILLED
      else -> State.REJECTED
    }
  }

  override fun done(done: Consumer<in T>): Promise<T> {
    setHandler(doneRef, done, State.FULFILLED)
    return this
  }

  override fun rejected(rejected: Consumer<Throwable>): Promise<T> {
    setHandler(rejectedRef, rejected, State.REJECTED)
    return this
  }

  override fun get(): T? {
    val value = valueRef.get()
    return if (value != null && value.error == null) value.result else null
  }

  override fun <SUB_RESULT> then(handler: Function<in T, out SUB_RESULT>): Promise<SUB_RESULT> {
    val value = valueRef.get()
    when {
      value == null -> {
      }
      value.error == null -> return resolvedPromise(handler.`fun`(value.result))
      else -> return RejectedPromise(value.error)
    }

    val promise = AsyncPromise<SUB_RESULT>()
    addHandlers(Consumer({ result ->
                           promise.catchError {
                             if (handler is Obsolescent && handler.isObsolete) {
                               promise.cancel()
                             }
                             else {
                               promise.setResult(handler.`fun`(result))
                             }
                           }
                         }), Consumer({ promise.setError(it) }))
    return promise
  }

  override fun <SUB_RESULT> thenAsync(handler: Function<in T, Promise<SUB_RESULT>>): Promise<SUB_RESULT> {
    val value = valueRef.get()
    when {
      value == null -> {
      }
      value.error == null -> return handler.`fun`(value.result)
      else -> return RejectedPromise(value.error)
    }

    val promise = AsyncPromise<SUB_RESULT>()
    val rejectedHandler = Consumer<Throwable>({ promise.setError(it) })
    addHandlers(Consumer({
                           promise.catchError {
                             handler.`fun`(it)
                                 .done { promise.catchError { promise.setResult(it) } }
                                 .rejected(rejectedHandler)
                           }
                         }), rejectedHandler)
    return promise
  }

  override fun processed(child: Promise<in T>): Promise<T> {
    if (child.state != State.PENDING || child !is AsyncPromise) {
      return this
    }

    val value = valueRef.get()
    when {
      value == null -> addHandlers(Consumer({ child.catchError { child.setResult(it) } }), Consumer({ child.setError(it) }))
      value.error == null -> child.setResult(value.result)
      else -> child.setError(value.error)
    }
    return this
  }

  private fun addHandlers(done: Consumer<T>, rejected: Consumer<Throwable>) {
    setHandler(doneRef, done, State.FULFILLED)
    setHandler(rejectedRef, rejected, State.REJECTED)
  }

  fun setResult(result: T?) {
    if (!valueRef.compareAndSet(null, PromiseValue(result = result))) {
      return
    }

    val done = doneRef.getAndSet(null)
    rejectedRef.set(null)

    if (done != null && !isObsolete(done)) {
      done.consume(result)
    }
  }

  fun setError(error: String) = setError(createError(error))

  override fun cancel() {
    setError(OBSOLETE_ERROR)
  }

  open fun setError(error: Throwable): Boolean {
    if (!valueRef.compareAndSet(null, PromiseValue(error = error))) {
      LOG.errorIfNotMessage(error)
      return false
    }

    val rejected = rejectedRef.getAndSet(null)
    doneRef.set(null)

    if (rejected == null) {
      LOG.errorIfNotMessage(error)
    }
    else if (!isObsolete(rejected)) {
      rejected.consume(error)
    }
    return true
  }

  override fun processed(processed: Consumer<in T>): Promise<T> {
    done(processed)
    rejected { processed.consume(null) }
    return this
  }

  override fun blockingGet(timeout: Int, timeUnit: TimeUnit): T? {
    var value = valueRef.get()
    if (value == null) {
      val latch = CountDownLatch(1)
      processed { latch.countDown() }
      if (!latch.await(timeout.toLong(), timeUnit)) {
        throw TimeoutException()
      }

      value = valueRef.get()!!
    }

    val error = value.error
    if (error == null) {
      return value.result
    }
    else {
      throw error
    }
  }

  private fun <T> setHandler(ref: AtomicReference<Consumer<in T>?>, newConsumer: Consumer<in T>, targetState: State) {
    if (isObsolete(newConsumer)) {
      return
    }

    valueRef.get()?.let {
      callConsumerIfTargeted(targetState, newConsumer, it)
      return
    }

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
            valueRef.get()?.let {
              callConsumerIfTargeted(targetState, newConsumer, it)
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

    if (state == targetState) {
      ref.getAndSet(null)?.let {
        callConsumerIfTargeted(targetState, it, valueRef.get()!!)
      }
    }
  }

  private fun <C_T> callConsumerIfTargeted(targetState: State, newConsumer: Consumer<in C_T>, value: PromiseValue<T>) {
    val currentState = if (value.error == null) State.FULFILLED else State.REJECTED
    if (currentState == targetState) {
      @Suppress("UNCHECKED_CAST")
      newConsumer.consume(if (currentState == State.FULFILLED) value.result as C_T? else value.error as C_T)
    }
  }

  override fun get(timeout: Long, unit: TimeUnit): T? {
    return blockingGet(timeout.toInt(), unit)
  }

  override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
    if (state == State.PENDING) {
      cancel()
      return true
    }
    else {
      return false
    }
  }

  override fun isCancelled(): Boolean {
    return valueRef.get()?.error == OBSOLETE_ERROR
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