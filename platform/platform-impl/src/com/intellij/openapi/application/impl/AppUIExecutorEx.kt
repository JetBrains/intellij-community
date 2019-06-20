// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ExpirableExecutor
import com.intellij.openapi.application.constraints.ConstrainedExecution
import com.intellij.openapi.application.constraints.cancelJobOnDisposal
import kotlinx.coroutines.*
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * @author eldar
 */

fun AppUIExecutor.inUndoTransparentAction(): AppUIExecutor =
  (this as AppUIExecutorImpl).inUndoTransparentAction()
fun AppUIExecutor.inWriteAction():AppUIExecutor =
  (this as AppUIExecutorImpl).inWriteAction()

fun AppUIExecutor.withConstraint(constraint: ConstrainedExecution.ContextConstraint): AppUIExecutor =
  (this as AppUIExecutorImpl).withConstraint(constraint)
fun AppUIExecutor.withConstraint(constraint: ConstrainedExecution.ContextConstraint, parentDisposable: Disposable): AppUIExecutor =
  (this as AppUIExecutorImpl).withConstraint(constraint, parentDisposable)

fun ExpirableExecutor.withConstraint(constraint: ConstrainedExecution.ContextConstraint): ExpirableExecutor =
  (this as ExpirableExecutorImpl).withConstraint(constraint)
fun ExpirableExecutor.withConstraint(constraint: ConstrainedExecution.ContextConstraint, parentDisposable: Disposable): ExpirableExecutor =
  (this as ExpirableExecutorImpl).withConstraint(constraint, parentDisposable)

/**
 * A [context][CoroutineContext] to be used with the standard [launch], [async], [withContext] coroutine builders.
 * Contains: [ContinuationInterceptor].
 */
fun ExpirableExecutor.coroutineDispatchingContext(): ContinuationInterceptor =
  (this as ExpirableExecutorImpl).asCoroutineDispatcher()

fun AppUIExecutor.coroutineDispatchingContext(): ContinuationInterceptor =
  (this as AppUIExecutorImpl).asCoroutineDispatcher()


@Throws(CancellationException::class)
suspend fun <T> runUnlessDisposed(disposable: Disposable, block: suspend () -> T): T {
  return coroutineScope {
    disposable.cancelJobOnDisposal(coroutineContext[Job]!!).use {
      block()
    }
  }
}
