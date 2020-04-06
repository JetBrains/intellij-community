// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.operations

import com.intellij.openapi.observable.operations.CompoundParallelOperationTrace.Companion.task

class AnonymousParallelOperationTrace(debugName: String? = null) {
  private val delegate = CompoundParallelOperationTrace<Nothing?>(debugName)

  fun isOperationCompleted() = delegate.isOperationCompleted()
  fun startTask() = delegate.startTask(null)
  fun finishTask() = delegate.finishTask(null)
  fun beforeOperation(listener: CompoundParallelOperationTrace.Listener) = delegate.beforeOperation(listener)
  fun beforeOperation(listener: () -> Unit) = delegate.beforeOperation(listener)
  fun afterOperation(listener: CompoundParallelOperationTrace.Listener) = delegate.afterOperation(listener)
  fun afterOperation(listener: () -> Unit) = delegate.afterOperation(listener)

  companion object {
    fun <R> AnonymousParallelOperationTrace.task(action: () -> R): R = delegate.task(null, action)
  }
}