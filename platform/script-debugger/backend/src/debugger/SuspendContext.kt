// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger

import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.values.ValueManager

/**
 * An object that matches the execution state of the VM while suspended
 */
interface SuspendContext<out CALL_FRAME : CallFrame> {
  val state: SuspendState

  val script: Script?
    get() = topFrame?.let { vm.scriptManager.getScript(it) }

  /**
   * @return the current exception state, or `null` if current state is
   * *         not `EXCEPTION`
   * *
   * @see .getState
   */
  val exceptionData: ExceptionData?
    get() = null

  val topFrame: CALL_FRAME?

  /**
   * Call frames for the current suspended state (from the innermost (top) frame to the main (bottom) frame)
   */
  val frames: Promise<Array<CallFrame>>

  /**
   * list of the breakpoints hit on VM suspension with which this
   * context is associated. An empty collection if the suspension was
   * not related to hitting breakpoints (e.g. a step end)
   */
  val breakpointsHit: List<Breakpoint>

  val hasUnresolvedBreakpointsHit: Boolean
    get() = false

  val valueManager: ValueManager

  val vm: Vm
    get() = throw UnsupportedOperationException()
}

abstract class ContextDependentAsyncResultConsumer<T>(private val context: SuspendContext<*>) : java.util.function.Consumer<T> {
  override final fun accept(result: T) {
    val vm = context.vm
    if (vm.attachStateManager.isAttached && !vm.suspendContextManager.isContextObsolete(context)) {
      accept(result, vm)
    }
  }

  protected abstract fun accept(result: T, vm: Vm)
}


inline fun <T> Promise<T>.onSuccess(context: SuspendContext<*>, crossinline handler: (result: T) -> Unit): Promise<T> {
  return onSuccess(object : ContextDependentAsyncResultConsumer<T>(context) {
    override fun accept(result: T, vm: Vm) = handler(result)
  })
}

inline fun Promise<*>.onError(context: SuspendContext<*>, crossinline handler: (error: Throwable) -> Unit): Promise<out Any> {
  return onError(object : ContextDependentAsyncResultConsumer<Throwable>(context) {
    override fun accept(result: Throwable, vm: Vm) = handler(result)
  })
}