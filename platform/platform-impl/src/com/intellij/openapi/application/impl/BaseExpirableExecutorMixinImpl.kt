// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.constraints.ConstrainedExecution
import com.intellij.openapi.application.constraints.ExpirableConstrainedExecution
import com.intellij.openapi.application.constraints.Expiration
import org.jetbrains.concurrency.CancellablePromise
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.function.BooleanSupplier

internal abstract class BaseExpirableExecutorMixinImpl<E : BaseExpirableExecutorMixinImpl<E>>
  protected constructor(constraints: Array<ConstrainedExecution.ContextConstraint>,
                        cancellationConditions: Array<BooleanSupplier>,
                        expirableHandles: Set<Expiration>,
                        private val executor: Executor)
  : ConstrainedExecution<E>,
    ExpirableConstrainedExecution<E>(constraints, cancellationConditions, expirableHandles) {

  constructor (executor: Executor) : this(emptyArray(), emptyArray(), emptySet(), executor)

  override fun scheduleWithinConstraints(runnable: Runnable, condition: BooleanSupplier?) {
    executor.execute(kotlinx.coroutines.Runnable {
      super.scheduleWithinConstraints(runnable, condition)
    })
  }

  fun execute(command: Runnable): Unit = asExecutor().execute(command)
  fun submit(task: Runnable): CancellablePromise<*> = asExecutor().submit(task)
  fun <T : Any?> submit(task: Callable<T>): CancellablePromise<T> = asExecutor().submit(task)
}