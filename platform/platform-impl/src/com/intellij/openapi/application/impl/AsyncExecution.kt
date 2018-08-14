// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.openapi.Disposable
import kotlinx.coroutines.experimental.CoroutineDispatcher
import kotlin.coroutines.experimental.ContinuationInterceptor

/**
 * @author eldar
 */
interface AsyncExecution : ContinuationInterceptor {

  interface ContextConstraint {
    val isCorrectContext: Boolean

    fun toCoroutineDispatcher(delegate: CoroutineDispatcher): CoroutineDispatcher

    override fun toString(): String
  }

  /**
   * Implementation MUST guarantee to execute a runnable passed to [schedule] at some point.
   * For dispatchers that may refuse to run the task based on some condition
   * consider using [ExpirableContextConstraint] instead.
   */
  interface SimpleContextConstraint : ContextConstraint {
    fun schedule(runnable: Runnable)

    override fun toCoroutineDispatcher(delegate: CoroutineDispatcher): CoroutineDispatcher =
      AsyncExecutionSupport.SimpleConstraintDispatcher(delegate, this)
  }

  /**
   * This class ensures that a coroutine continuation is invoked at some point
   * even if the underlying dispatcher doesn't usually run a task once some [Disposable] is disposed.
   *
   * At the very least, the implementation MUST guarantee to execute a runnable passed to [scheduleExpirable]
   * if the corresponding [expirable] is still not disposed by the time the dispatcher arranges the proper execution context.
   * It is OK to execute it after the [expirable] has been disposed though.
   */
  interface ExpirableContextConstraint : ContextConstraint {
    val expirable: Disposable
    fun scheduleExpirable(runnable: Runnable)

    override fun toCoroutineDispatcher(delegate: CoroutineDispatcher): CoroutineDispatcher =
      AsyncExecutionSupport.ExpirableConstraintDispatcher(delegate, this)
  }
}