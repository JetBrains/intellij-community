// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("ClientIdPropagation")

package com.intellij.concurrency.client

import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Callable
import java.util.function.BiConsumer
import java.util.function.Function

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

internal fun withClientId(clientId: String?, action: Runnable) = withClientId(clientId, action::run)
internal fun <T> withClientId(clientId: String?, callable: Callable<T>) = withClientId(clientId, callable::call)

fun captureClientIdInRunnable(runnable: Runnable): Runnable {
  if (!propagateClientIdAcrossThreads) return runnable
  val currentId = currentClientIdString
  return Runnable {
    withClientId(currentId) {
      runnable.run()
    }
  }
}

fun <T> captureClientIdInCallable(callable: Callable<T>): Callable<T> {
  if (!propagateClientIdAcrossThreads) return callable
  val currentId = currentClientIdString
  return Callable {
    withClientId(currentId) {
      callable.call()
    }
  }
}

fun <T> captureClientIdInProcessor(processor: Processor<T>): Processor<T> {
  if (!propagateClientIdAcrossThreads) return processor
  val currentId = currentClientIdString
  return Processor {
    withClientId(currentId) {
      processor.process(it)
    }
  }
}

fun <T> captureClientId(action: () -> T): () -> T {
  if (propagateClientIdAcrossThreads) return action
  val currentId = currentClientIdString
  return {
    withClientId(currentId) {
      action()
    }
  }
}

fun <T, R> captureClientIdInFunction(function: Function<T, R>): Function<T, R> {
  if (!propagateClientIdAcrossThreads) return function
  val currentId = currentClientIdString
  return Function {
    withClientId(currentId) {
      function.apply(it)
    }
  }
}

fun <T, U> captureClientIdInBiConsumer(biConsumer: BiConsumer<T, U>): BiConsumer<T, U> {
  if (!propagateClientIdAcrossThreads) return biConsumer
  val currentId = currentClientIdString
  return BiConsumer { t, u ->
    withClientId(currentId) {
      biConsumer.accept(t, u)
    }
  }
}
