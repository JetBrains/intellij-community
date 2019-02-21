// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.constraints

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import java.util.concurrent.Executor
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

internal fun createConstrainedCoroutineDispatcher(executor: Executor,
                                                  expiration: Expiration? = null): ContinuationInterceptor {
  val dispatcher = ExecutorDispatcher(executor)
  return when (expiration) {
    null -> dispatcher
    else -> ExpirableContinuationInterceptor(dispatcher, expiration)
  }
}

internal class ExecutorDispatcher(private val executor: Executor) : CoroutineDispatcher() {
  override fun dispatch(context: CoroutineContext, block: Runnable) = executor.execute(block)
  override fun toString(): String = executor.toString()
}

internal class ExpirableContinuationInterceptor(private val dispatcher: CoroutineDispatcher,
                                                private val expiration: Expiration)
  : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
  /** Invoked once on each newly launched coroutine when dispatching it for the first time. */
  override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
    expiration.cancelJobOnExpiration(continuation.context[Job]!!)
    return dispatcher.interceptContinuation(continuation)
  }

  override fun toString(): String = dispatcher.toString()
}
