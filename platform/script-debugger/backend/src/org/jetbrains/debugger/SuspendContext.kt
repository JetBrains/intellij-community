/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.debugger

import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.values.ValueManager

/**
 * An object that matches the execution state of the VM while suspended
 */
interface SuspendContext<CALL_FRAME : CallFrame> {
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

  val topFrame: CALL_FRAME?

  /**
   * Call frames for the current suspended state (from the innermost (top) frame to the main (bottom) frame)
   */
  val frames: Promise<Array<CALL_FRAME>>

  /**
   * list of the breakpoints hit on VM suspension with which this
   * context is associated. An empty collection if the suspension was
   * not related to hitting breakpoints (e.g. a step end)
   */
  val breakpointsHit: List<Breakpoint>

  val valueManager: ValueManager
}
