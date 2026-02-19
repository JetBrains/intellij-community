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

import com.intellij.openapi.util.UserDataHolderEx
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.nullPromise

@ApiStatus.NonExtendable
interface Vm : UserDataHolderEx {
  @get:ApiStatus.Internal
  val debugListener: DebugEventListener

  @get:ApiStatus.Internal
  val attachStateManager: AttachStateManager

  @get:ApiStatus.Internal
  val evaluateContext: EvaluateContext?

  @get:ApiStatus.Internal
  val scriptManager: ScriptManager

  @get:ApiStatus.Internal
  val breakpointManager: BreakpointManager

  @get:ApiStatus.Internal
  val suspendContextManager: SuspendContextManager<out CallFrame>

  /**
   * Controls whether VM stops on exceptions
   */
  @ApiStatus.Internal
  fun setBreakOnException(catchMode: ExceptionCatchMode): Promise<*> = nullPromise()

  @get:ApiStatus.Internal
  val presentableName: String
    @Nls get() = ScriptDebuggerBundle.message("debug.vm.title.main.thread")
}