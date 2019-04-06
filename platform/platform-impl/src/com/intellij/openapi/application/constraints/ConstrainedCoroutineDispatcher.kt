// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.constraints

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import java.util.function.BooleanSupplier
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

internal fun createConstrainedCoroutineDispatcher(executionScheduler: ConstrainedExecutionScheduler,
                                                  cancellationCondition: BooleanSupplier? = null,
                                                  expiration: Expiration? = null): ContinuationInterceptor {
  val dispatcher = ConstrainedCoroutineDispatcherImpl(executionScheduler, cancellationCondition)
  return when (expiration) {
    null -> dispatcher
    else -> ExpirableContinuationInterceptor(dispatcher, expiration)
  }
}

internal class ConstrainedCoroutineDispatcherImpl(private val executionScheduler: ConstrainedExecutionScheduler,
                                                  private val cancellationCondition: BooleanSupplier?) : CoroutineDispatcher() {
  override fun dispatch(context: CoroutineContext, block: Runnable) {
    val condition = cancellationCondition?.let {
      BooleanSupplier {
        true.also {
          if (cancellationCondition.asBoolean) context.cancel()
        }
      }
    }
    executionScheduler.scheduleWithinConstraints(block, condition)
  }
  override fun toString(): String = executionScheduler.toString()
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
