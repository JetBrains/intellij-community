/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

interface SuspendContextManager<CALL_FRAME : CallFrame> {
  /**
   * Tries to suspend VM. If successful, [DebugEventListener.suspended] will be called.
   */
  fun suspend(): Promise<*>

  val context: SuspendContext<CALL_FRAME>?

  val contextOrFail: SuspendContext<CALL_FRAME>

  fun isContextObsolete(context: SuspendContext<*>) = this.context !== context

  fun setOverlayMessage(message: String?)

  /**
   * Resumes the VM execution. This context becomes invalid until another context is supplied through the
   * [DebugEventListener.suspended] event.
   * @param stepAction to perform
   * *
   * @param stepCount steps to perform (not used if `stepAction == CONTINUE`)
   */
  fun continueVm(stepAction: StepAction, stepCount: Int = 1): Promise<*>

  val isRestartFrameSupported: Boolean

  /**
   * Restarts a frame (all frames above are dropped from the stack, this frame is started over).
   * for success the boolean parameter
   * is true if VM has been resumed and is expected to get suspended again in a moment (with
   * a standard 'resumed' notification), and is false if call frames list is already updated
   * without VM state change (this case presently is never actually happening)
   */
  fun restartFrame(callFrame: CALL_FRAME): Promise<Boolean>

  /**
   * @return whether reset operation is supported for the particular callFrame
   */
  fun canRestartFrame(callFrame: CallFrame): Boolean
}

enum class StepAction {
  /**
   * Resume the JavaScript execution.
   */
  CONTINUE,

  /**
   * Step into the current statement.
   */
  IN,

  /**
   * Step over the current statement.
   */
  OVER,

  /**
   * Step out of the current function.
   */
  OUT
}