// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("ClientIdPropagation")

package com.intellij.concurrency.client

import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

private val threadLocalClientIdString = ThreadLocal.withInitial<String?> { null }
@get:ApiStatus.Internal
@set:ApiStatus.Internal
var currentClientIdString: String?
  get() = threadLocalClientIdString.get()
  set(value) = threadLocalClientIdString.set(value)

@get:ApiStatus.Internal
@set:ApiStatus.Internal
var propagateClientIdAcrossThreads: Boolean = false

private inline fun <T> withClientId(clientId: String?, action: () -> T): T {
  val oldClientIdValue = currentClientIdString
  try {
    currentClientIdString = clientId
    return action()
  }
  finally {
    currentClientIdString = oldClientIdValue
  }
}

fun decorateRunnable(runnable: Runnable): Runnable {
  if (!propagateClientIdAcrossThreads) return runnable
  val currentId = currentClientIdString
  return Runnable {
    withClientId(currentId) {
      runnable.run()
    }
  }
}

fun <T> decorateCallable(callable: Callable<T>): Callable<T> {
  if (!propagateClientIdAcrossThreads) return callable
  val currentId = currentClientIdString
  return Callable {
    withClientId(currentId) {
      callable.call()
    }
  }
}

fun <T> decorateProcessor(processor: Processor<T>): Processor<T> {
  if (!propagateClientIdAcrossThreads) return processor
  val currentId = currentClientIdString
  return Processor {
    withClientId(currentId) {
      processor.process(it)
    }
  }
}

fun <T> decorateFunction(action: () -> T): () -> T {
  if (propagateClientIdAcrossThreads) return action
  val currentId = currentClientIdString
  return {
    withClientId(currentId) {
      action()
    }
  }
}

fun <T, R> decorateFunction(function: Function<T, R>): Function<T, R> {
  if (!propagateClientIdAcrossThreads) return function
  val currentId = currentClientIdString
  return Function {
    withClientId(currentId) {
      function.apply(it)
    }
  }
}

fun <T> decorateSupplier(supplier: Supplier<T>): Supplier<T> {
  if (!propagateClientIdAcrossThreads) return supplier
  val currentId = currentClientIdString
  return Supplier {
    withClientId(currentId) {
      supplier.get()
    }
  }
}

fun <T> decorateConsumer(consumer: Consumer<T>): Consumer<T> {
  if (!propagateClientIdAcrossThreads) return consumer
  val currentId = currentClientIdString
  return Consumer { t ->
    withClientId(currentId) {
      consumer.accept(t)
    }
  }
}

fun <T, U> decorateBiConsumer(biConsumer: BiConsumer<T, U>): BiConsumer<T, U> {
  if (!propagateClientIdAcrossThreads) return biConsumer
  val currentId = currentClientIdString
  return BiConsumer { t, u ->
    withClientId(currentId) {
      biConsumer.accept(t, u)
    }
  }
}

fun <T> decorateFutureTask(futureTask: FutureTask<T>): FutureTask<T> {
  if (!propagateClientIdAcrossThreads) return futureTask
  val currentId = currentClientIdString
  return object : FutureTask<T>({ null }) {
    override fun run() {
      withClientId(currentId) {
        futureTask.run()
      }
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
      return futureTask.cancel(mayInterruptIfRunning)
    }

    override fun isCancelled(): Boolean {
      return futureTask.isCancelled
    }

    override fun isDone(): Boolean {
      return futureTask.isDone
    }

    override fun get(): T {
      return futureTask.get()
    }

    override fun get(timeout: Long, unit: TimeUnit): T {
      return futureTask.get(timeout, unit)
    }
  }
}