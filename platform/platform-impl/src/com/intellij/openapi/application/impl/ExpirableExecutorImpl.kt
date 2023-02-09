// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DeprecatedCallableAddReplaceWith")

package com.intellij.openapi.application.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ExpirableExecutor
import com.intellij.openapi.application.constraints.ConstrainedExecution.ContextConstraint
import com.intellij.openapi.application.constraints.Expiration
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import java.util.concurrent.Executor
import java.util.function.BooleanSupplier
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

@ScheduledForRemoval
@Deprecated(message = "This is a impl of a deprecated interface")
internal class ExpirableExecutorImpl private constructor(constraints: Array<ContextConstraint>,
                                                         cancellationConditions: Array<BooleanSupplier>,
                                                         expirableHandles: Set<Expiration>,
                                                         private val executor: Executor)
  : ExpirableExecutor, BaseExpirableExecutorMixinImpl<ExpirableExecutorImpl>(constraints, cancellationConditions, expirableHandles,
                                                                             executor) {

  constructor (executor: Executor) : this(emptyArray(), emptyArray(), emptySet(), executor)

  override fun cloneWith(constraints: Array<ContextConstraint>,
                         cancellationConditions: Array<BooleanSupplier>,
                         expirationSet: Set<Expiration>): ExpirableExecutorImpl =
    ExpirableExecutorImpl(constraints, cancellationConditions, expirationSet, executor)

  override fun dispatchLaterUnconstrained(runnable: Runnable) =
    executor.execute(runnable)
}

@ScheduledForRemoval
@Deprecated(message = "This constraint only holds inside a read action, " +
                      "the executor cannot ever guarantee that it will hold at the moment of execution")
fun ExpirableExecutor.withConstraint(constraint: ContextConstraint): ExpirableExecutor =
  (this as ExpirableExecutorImpl).withConstraint(constraint)

@ScheduledForRemoval
@Deprecated(message = "This constraint only holds inside a read action, " +
                      "the executor cannot ever guarantee that it will hold at the moment of execution")
fun ExpirableExecutor.withConstraint(constraint: ContextConstraint, parentDisposable: Disposable): ExpirableExecutor =
  (this as ExpirableExecutorImpl).withConstraint(constraint, parentDisposable)

/**
 * A [context][CoroutineContext] to be used with the standard [launch], [async], [withContext] coroutine builders.
 * Contains: [ContinuationInterceptor].
 */
@ScheduledForRemoval
@Deprecated(message = "Do not use: coroutine cancellation must not be handled by a dispatcher.")
fun ExpirableExecutor.coroutineDispatchingContext(): ContinuationInterceptor =
  (this as ExpirableExecutorImpl).asCoroutineDispatcher()
