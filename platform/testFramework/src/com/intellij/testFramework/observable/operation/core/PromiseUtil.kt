// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.observable.operation.core

import com.intellij.openapi.observable.operation.core.ObservableOperationTrace
import com.intellij.openapi.observable.operation.core.getOperationFinishPromise
import com.intellij.openapi.observable.operation.core.getOperationStartPromise
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.use
import com.intellij.testFramework.concurrency.waitForPromise
import com.intellij.testFramework.concurrency.awaitPromise
import org.jetbrains.concurrency.Promise
import kotlin.time.Duration

fun <R> ObservableOperationTrace.waitForOperation(
  startTimeout: Duration,
  finishTimeout: Duration,
  action: ThrowableComputable<R, Throwable>
): R = waitForOperation(startTimeout, finishTimeout, { waitForPromise(it) }, { action.compute() })

suspend fun <R> ObservableOperationTrace.awaitOperation(
  startTimeout: Duration,
  finishTimeout: Duration,
  action: suspend () -> R
): R = waitForOperation(startTimeout, finishTimeout, { awaitPromise(it) }, { action() })

private inline fun <R> ObservableOperationTrace.waitForOperation(
  startTimeout: Duration,
  finishTimeout: Duration,
  wait: Promise<*>.(Duration) -> Unit,
  action: () -> R
): R {
  return Disposer.newDisposable().use { parentDisposable ->
    val startPromise = getOperationStartPromise(parentDisposable)
    val finishPromise = getOperationFinishPromise(parentDisposable)
    val result = action()
    try {
      startPromise.wait(startTimeout)
    }
    catch (ex: Throwable) {
      throw IllegalStateException("Operation '$name' didn't started during $startTimeout.\n$this", ex)
    }
    try {
      finishPromise.wait(finishTimeout)
    }
    catch (ex: Throwable) {
      throw IllegalStateException("Operation '$name' didn't finished during $finishTimeout.\n$this", ex)
    }
    result
  }
}
