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

import org.jetbrains.util.concurrency.Promise
import org.jetbrains.util.concurrency.ResolvedPromise

public interface AttachStateManager {
  public fun detach(): Promise<*> = ResolvedPromise()

  public fun isAttached(): Boolean = true
}

public interface Vm {
  public val debugListener: DebugEventListener

  public val attachStateManager: AttachStateManager

  public fun getScriptManager(): ScriptManager

  public fun getBreakpointManager(): BreakpointManager

  public fun getSuspendContextManager(): SuspendContextManager<out CallFrame>

  /**
   * Controls whether VM stops on exceptions
   */
  public fun setBreakOnException(catchMode: ExceptionCatchMode): Promise<*>

  public fun getEvaluateContext(): EvaluateContext?
}