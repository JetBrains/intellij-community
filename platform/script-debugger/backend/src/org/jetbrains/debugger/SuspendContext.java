package org.jetbrains.debugger

import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.values.ValueManager

/**
 * An object that matches the execution state of the VM while suspended
 */
interface SuspendContext {
  val state: SuspendState

  val script: Script?

  /**
   * @return the current exception state, or `null` if current state is
   * *         not `EXCEPTION`
   * *
   * @see .getState
   */
  val exceptionData: ExceptionData?
    get() = null

  val topFrame: CallFrame?

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

  val valueManager: ValueManager<out Vm>
}
