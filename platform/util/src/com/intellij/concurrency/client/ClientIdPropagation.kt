// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("ClientIdPropagation")

package com.intellij.concurrency.client

import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.Callable
import java.util.function.BiConsumer
import java.util.function.Function
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

private val logger = logger<ClientIdStringContextElement>()

@ApiStatus.Internal
class ClientIdStringContextElement(val clientIdString: String?) : AbstractCoroutineContextElement(Key) {
  private val creationTrace: Throwable? = if (logger.isTraceEnabled) Throwable() else null
  object Key : CoroutineContext.Key<ClientIdStringContextElement>

  override fun toString(): String = if (creationTrace != null) "ClientId=$clientIdString. Created at:\r$creationTrace" else "ClientId=$clientIdString"
}

val CoroutineContext.clientIdStringContextElement: ClientIdStringContextElement?
  get() = this[ClientIdStringContextElement.Key]

val currentThreadClientIdString: String? get() = currentThreadContext().clientIdStringContextElement?.clientIdString

@get:ApiStatus.Internal
@set:ApiStatus.Internal
var propagateClientIdAcrossThreads: Boolean = false

inline fun <T> withClientId(clientId: String?, action: () -> T): T {
  return withClientId(ClientIdStringContextElement(clientId), action)
}

inline fun <T> withClientId(idStringContextElement: ClientIdStringContextElement, action: () -> T): T {
  installThreadContext(idStringContextElement, replace = true).use {
    return action()
  }
}

internal fun withClientId(clientId: String?, action: Runnable) = withClientId(clientId, action::run)
internal fun <T> withClientId(clientId: String?, callable: Callable<T>) = withClientId(clientId, callable::call)

@ApiStatus.Internal
fun captureClientIdInRunnable(runnable: Runnable): Runnable {
  return runnable
  if (!propagateClientIdAcrossThreads) return runnable
  val idStringContextElement = currentThreadContext().clientIdStringContextElement ?: return runnable
  val currentId = idStringContextElement.clientIdString
  return Runnable {
    withClientId(currentId) {
      runnable.run()
    }
  }
}

@ApiStatus.Internal
fun <T> captureClientIdInCallable(callable: Callable<T>): Callable<T> {
  return callable
  if (!propagateClientIdAcrossThreads) return callable
  val idStringContextElement = currentThreadContext().clientIdStringContextElement ?: return callable
  return Callable {
    withClientId(idStringContextElement) {
      callable.call()
    }
  }
}

@ApiStatus.Internal
fun <T> captureClientIdInProcessor(processor: Processor<T>): Processor<T> {
  return processor
  if (!propagateClientIdAcrossThreads) return processor
  val idStringContextElement = currentThreadContext().clientIdStringContextElement ?: return processor
  return Processor {
    withClientId(idStringContextElement) {
      processor.process(it)
    }
  }
}

@ApiStatus.Internal
fun <T> captureClientId(action: () -> T): () -> T {
  return action
  if (propagateClientIdAcrossThreads) return action
  val idStringContextElement = currentThreadContext().clientIdStringContextElement ?: return action
  return {
    withClientId(idStringContextElement) {
      action()
    }
  }
}

@ApiStatus.Internal
fun <T, R> captureClientIdInFunction(function: Function<T, R>): Function<T, R> {
  return function
  if (!propagateClientIdAcrossThreads) return function
  val idStringContextElement = currentThreadContext().clientIdStringContextElement ?: return function
  return Function {
    withClientId(idStringContextElement) {
      function.apply(it)
    }
  }
}

@ApiStatus.Internal
fun <T, U> captureClientIdInBiConsumer(biConsumer: BiConsumer<T, U>): BiConsumer<T, U> {
  return biConsumer
  if (!propagateClientIdAcrossThreads) return biConsumer
  val idStringContextElement = currentThreadContext().clientIdStringContextElement ?: return biConsumer
  return BiConsumer { t, u ->
    withClientId(idStringContextElement) {
      biConsumer.accept(t, u)
    }
  }
}
