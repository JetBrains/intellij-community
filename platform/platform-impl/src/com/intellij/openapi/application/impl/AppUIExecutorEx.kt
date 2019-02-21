// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.constraints.ConstrainedExecution
import com.intellij.openapi.application.constraints.cancelJobOnDisposal
import kotlinx.coroutines.*
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * @author eldar
 */
interface AppUIExecutorEx : AppUIExecutor, ConstrainedExecution<AppUIExecutorEx> {
  fun asCoroutineContext(): CoroutineContext
  fun inUndoTransparentAction(): AppUIExecutor
  fun inWriteAction(): AppUIExecutor
}

fun AppUIExecutor.inUndoTransparentAction() =
  (this as AppUIExecutorEx).inUndoTransparentAction()
fun AppUIExecutor.inWriteAction() =
  (this as AppUIExecutorEx).inWriteAction()

fun AppUIExecutor.withConstraint(constraint: ConstrainedExecution.ContextConstraint): AppUIExecutor =
  (this as AppUIExecutorEx).withConstraint(constraint)
fun AppUIExecutor.withConstraint(constraint: ConstrainedExecution.ContextConstraint, parentDisposable: Disposable): AppUIExecutor =
  (this as AppUIExecutorEx).withConstraint(constraint, parentDisposable)

/**
 * A [context][CoroutineContext] to be used with the standard [launch], [async], [withContext] coroutine builders.
 * Contains: [ContinuationInterceptor].
 */
fun AppUIExecutor.coroutineDispatchingContext(): CoroutineContext =
  (this as AppUIExecutorEx).asCoroutineContext()


@Throws(CancellationException::class)
suspend fun <T> runUnlessDisposed(disposable: Disposable, block: suspend () -> T): T {
  return coroutineScope {
    disposable.cancelJobOnDisposal(coroutineContext[Job]!!).use {
      block()
    }
  }
}
