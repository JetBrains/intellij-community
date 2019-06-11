// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.constraints.ConstrainedExecution
import com.intellij.openapi.application.constraints.ExpirableConstrainedExecution
import com.intellij.openapi.application.constraints.Expiration
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import org.jetbrains.concurrency.CancellablePromise
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.function.BooleanSupplier

internal abstract class BaseAppExecutorMixinImpl<E : BaseAppExecutorMixinImpl<E>>
  protected constructor(constraints: Array<ConstrainedExecution.ContextConstraint>,
                        cancellationConditions: Array<BooleanSupplier>,
                        expirableHandles: Set<Expiration>,
                        private val executor: Executor = Executor { it.run() })
  : ConstrainedExecution<E>,
    ExpirableConstrainedExecution<E>(constraints, cancellationConditions, expirableHandles) {

  constructor (executor: Executor = Executor { it.run() }) : this(emptyArray(), emptyArray(), emptySet(), executor)

  override fun scheduleWithinConstraints(runnable: Runnable, condition: BooleanSupplier?) {
    executor.execute(
      kotlinx.coroutines.Runnable { super.scheduleWithinConstraints(runnable, condition) }
    )
  }

  override fun dispatchLaterUnconstrained(runnable: Runnable) =
    executor.execute(runnable)

  fun execute(command: Runnable): Unit = asExecutor().execute(command)
  fun submit(task: Runnable): CancellablePromise<*> = asExecutor().submit(task)
  fun <T : Any?> submit(task: Callable<T>): CancellablePromise<T> = asExecutor().submit(task)

  fun inSmartMode(project: Project): E {
    return withConstraint(InSmartMode(project), project)
  }

  fun inReadAction(): E {
    return withConstraint(InReadAction())
  }
}

internal class InSmartMode(private val project: Project) : ConstrainedExecution.ContextConstraint {
  override fun isCorrectContext(): Boolean =
    !DumbService.getInstance(project).isDumb

  override fun schedule(runnable: Runnable) {
    DumbService.getInstance(project).runWhenSmart(runnable)
  }

  override fun toString() = "inSmartMode"
}

internal class InReadAction() : ConstrainedExecution.ContextConstraint {
  override fun isCorrectContext(): Boolean =
    ApplicationManager.getApplication().isReadAccessAllowed

  override fun schedule(runnable: Runnable) {
    ApplicationManager.getApplication().runReadAction(runnable)
  }

  override fun toString() = "inReadAction"
}