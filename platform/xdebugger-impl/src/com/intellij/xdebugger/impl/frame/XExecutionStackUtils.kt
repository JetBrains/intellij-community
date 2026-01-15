// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext
import kotlinx.coroutines.CompletableDeferred
import org.jetbrains.annotations.ApiStatus

/**
 * Computes the list of running execution stacks for this debug process.
 *
 * This is a suspend wrapper around [XDebugProcess.computeRunningExecutionStacks] that converts
 * the callback-based API to a coroutine-based one.
 *
 * @return the list of currently running [XExecutionStack]s
 * @throws XExecutionStacksComputationException if an error occurs during computation
 */
@ApiStatus.Internal
suspend fun XDebugProcess.computeRunningExecutionStacksSuspend(): List<XExecutionStack> {
  return computeExecutionStacksImpl {
    computeRunningExecutionStacks(it)
  }
}

/**
 * Computes all execution stacks for this suspend context.
 *
 * This is a suspend wrapper around [XSuspendContext.computeExecutionStacks] that converts
 * the callback-based API to a coroutine-based one.
 *
 * @return the list of all [XExecutionStack]s in this suspend context
 * @throws XExecutionStacksComputationException if an error occurs during computation
 */
@ApiStatus.Internal
suspend fun XSuspendContext.computeExecutionStacksSuspend(): List<XExecutionStack> {
  return computeExecutionStacksImpl { container ->
    computeExecutionStacks(container)
  }
}

/**
 * Exception thrown when an error occurs during the computation of execution stacks.
 *
 * This exception wraps error messages reported via [com.intellij.xdebugger.frame.XSuspendContext.XExecutionStackContainer.errorOccurred].
 *
 * @param message the error message describing what went wrong during computation
 */
@ApiStatus.Internal
class XExecutionStacksComputationException(message: String) : Exception(message)

private suspend fun computeExecutionStacksImpl(
  computeExecutionStacks: (XSuspendContext.XExecutionStackContainer) -> Unit,
): List<XExecutionStack> {
  val result = mutableListOf<XExecutionStack>()
  val computationCompleted = CompletableDeferred<Unit>()
  var obsolete = false
  val container = object : XSuspendContext.XExecutionStackContainer {
    override fun isObsolete(): Boolean {
      return obsolete
    }

    override fun addExecutionStack(executionStacks: List<XExecutionStack>, last: Boolean) {
      result.addAll(executionStacks)
      if (last) {
        computationCompleted.complete(Unit)
      }
    }

    override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
      computationCompleted.completeExceptionally(XExecutionStacksComputationException(errorMessage))
    }
  }

  try {
    computeExecutionStacks(container)
    computationCompleted.await()
  }
  finally {
    obsolete = true
  }

  return result
}