// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ExpirableExecutor
import com.intellij.openapi.application.constraints.ConstrainedExecution
import com.intellij.openapi.application.constraints.Expiration
import java.util.concurrent.Executor
import java.util.function.BooleanSupplier

internal class ExpirableExecutorImpl private constructor(constraints: Array<ConstrainedExecution.ContextConstraint>,
                                                         cancellationConditions: Array<BooleanSupplier>,
                                                         expirableHandles: Set<Expiration>,
                                                         private val executor: Executor = Executor { it.run() })
  : ExpirableExecutor, BaseExpirableExecutorMixinImpl<ExpirableExecutorImpl>(constraints, cancellationConditions, expirableHandles, executor) {

  constructor (executor: Executor = Executor { it.run() }) : this(emptyArray(), emptyArray(), emptySet(), executor)

  override fun cloneWith(constraints: Array<ConstrainedExecution.ContextConstraint>,
                         cancellationConditions: Array<BooleanSupplier>,
                         expirationSet: Set<Expiration>): ExpirableExecutorImpl =
    ExpirableExecutorImpl(constraints, cancellationConditions, expirationSet, executor)
}