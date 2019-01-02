// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.async

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.cancel
import java.util.*
import kotlin.coroutines.CoroutineContext

// must be the ContinuationInterceptor in order to work properly
internal class RescheduleAttemptLimitAwareDispatcher(delegate: CoroutineDispatcher,
                                                     private val myLimit: Int = 3000)
  : BaseAsyncExecutionSupport.ChainedDispatcher(delegate) {
  private var myAttemptCount: Int = 0

  private val myLogLimit: Int = 30
  private val myLastDispatchers: Deque<CoroutineDispatcher> = ArrayDeque(myLogLimit)

  override fun isScheduleNeeded(context: CoroutineContext): Boolean = false
  override fun doSchedule(context: CoroutineContext,
                          retryDispatchRunnable: Runnable) = throw UnsupportedOperationException()

  override fun dispatch(context: CoroutineContext, block: Runnable) {
    resetAttemptCount()
    super.dispatch(context, block)
  }

  override fun retryDispatch(context: CoroutineContext,
                             block: Runnable,
                             causeDispatcher: BaseAsyncExecutionSupport.ChainedDispatcher) {
    if (checkHaveMoreRescheduleAttempts(causeDispatcher)) {
      super.dispatch(context, block)
    }
    else BaseAsyncExecutionSupport.run {
      processUncaughtException(context, TooManyRescheduleAttemptsException(myLastDispatchers))
      context.cancel()

      // The continuation block MUST be invoked at some point in order to give the coroutine a chance
      // to handle the cancellation exception and exit gracefully.
      // At this point we can only provide a guarantee to resume it on EDT with a proper modality state.
      fallbackDispatch(context, block)
    }
  }

  private fun resetAttemptCount() {
    myLastDispatchers.clear()
    myAttemptCount = 0
  }

  private fun checkHaveMoreRescheduleAttempts(dispatcher: CoroutineDispatcher): Boolean {
    with(myLastDispatchers) {
      if (isNotEmpty() && size >= myLogLimit) removeFirst()
      addLast(dispatcher)
    }
    return ++myAttemptCount < myLimit
  }

  /**
   * Thrown at a cancellation point when the executor is unable to arrange the requested context after a reasonable number of attempts.
   *
   * WARNING: The exception thrown is handled in a fallback context as a last resort,
   *          The fallback context is EDT with a proper modality state, no other guarantee is made.
   */
  internal class TooManyRescheduleAttemptsException internal constructor(lastConstraints: Collection<CoroutineDispatcher>)
    : Exception("Too many reschedule requests, probably constraints can't be satisfied all together: " + lastConstraints.joinToString())
}