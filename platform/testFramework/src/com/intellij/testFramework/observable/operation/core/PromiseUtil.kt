// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.observable.operation.core

import com.intellij.openapi.observable.operation.core.ObservableOperationTrace
import com.intellij.openapi.observable.operation.core.waitForOperation
import com.intellij.openapi.observable.operation.core.waitForOperationCompletion
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.testFramework.concurrency.waitForPromiseAndPumpEdt
import kotlin.time.Duration

fun <R> ObservableOperationTrace.waitForOperationAndPumpEdt(
  startTimeout: Duration,
  finishTimeout: Duration,
  action: ThrowableComputable<R, Throwable>
): R = waitForOperation(startTimeout, finishTimeout, { waitForPromiseAndPumpEdt(it) }, { action.compute() })

fun ObservableOperationTrace.waitForOperationCompletionAndPumpEdt(
  completionTimeout: Duration
): Unit = waitForOperationCompletion(completionTimeout) { waitForPromiseAndPumpEdt(it) }
