// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.constraints

import com.intellij.ide.plugins.PluginManager
import kotlinx.coroutines.*
import java.util.concurrent.Executor
import kotlin.LazyThreadSafetyMode.PUBLICATION
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * @author eldar
 */
class ConstrainedCoroutineSupport internal constructor(private val constrainedExecution: ConstrainedExecutionEx<*>) : CoroutineScope {
  override val coroutineContext: CoroutineContext by lazy(PUBLICATION) {
    val continuationInterceptor = createContinuationInterceptor()
    val coroutineName = CoroutineName("${javaClass.simpleName}($continuationInterceptor)")
    val exceptionHandler = CoroutineExceptionHandler { _, throwable -> processUncaughtException(throwable) }
    exceptionHandler + coroutineName + continuationInterceptor
  }

  private fun composeDispatchers(): CoroutineDispatcher =
    ExecutorDispatcher(constrainedExecution.createConstraintSchedulingExecutor())

  internal class ExecutorDispatcher(private val executor: Executor) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = executor.execute(block)
    override fun toString(): String = executor.toString()
  }

  private fun createContinuationInterceptor(): ContinuationInterceptor {
    val dispatcher = composeDispatchers()
    val expiration = constrainedExecution.composeExpiration() ?: return dispatcher

    return object : AbstractCoroutineContextElement(ContinuationInterceptor), ContinuationInterceptor {
      /** Invoked once on each newly launched coroutine when dispatching it for the first time. */
      override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        expiration.cancelJobOnExpiration(continuation.context[Job]!!)
        return dispatcher.interceptContinuation(continuation)
      }

      override fun toString(): String = dispatcher.toString()
    }
  }

  companion object {
    internal fun processUncaughtException(throwable: Throwable) {
      PluginManager.processException(throwable)
    }
  }
}