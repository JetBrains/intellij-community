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

import com.intellij.util.Url
import org.jetbrains.concurrency.Promise
import java.util.*

interface BreakpointManager {
  enum class MUTE_MODE {
    ALL,
    ONE,
    NONE
  }

  val breakpoints: Iterable<Breakpoint>

  val regExpBreakpointSupported: Boolean
    get() = false

  fun setBreakpoint(target: BreakpointTarget,
                    line: Int,
                    column: Int = Breakpoint.EMPTY_VALUE,
                    url: Url? = null,
                    condition: String? = null,
                    ignoreCount: Int = Breakpoint.EMPTY_VALUE): SetBreakpointResult

  fun remove(breakpoint: Breakpoint): Promise<*>

  /**
   * Supports targets that refer to function text in form of function-returning
   * JavaScript expression.
   * E.g. you can set a breakpoint on the 5th line of user method addressed as
   * 'PropertiesDialog.prototype.loadData'.
   * Expression is calculated immediately and never recalculated again.
   */
  val functionSupport: ((expression: String) -> BreakpointTarget)?
    get() = null

  // Could be called multiple times for breakpoint
  fun addBreakpointListener(listener: BreakpointListener)

  fun removeAll(): Promise<*>

  fun getMuteMode(): MUTE_MODE = BreakpointManager.MUTE_MODE.ONE

  /**
   * Flushes the breakpoint parameter changes (set* methods) into the browser
   * and returns a promise of an updated breakpoint. This method must
   * be called for the set* method invocations to take effect.
   */
  fun flush(breakpoint: Breakpoint): Promise<out Breakpoint>

  /**
   * Asynchronously enables or disables all breakpoints on remote. 'Enabled' means that
   * breakpoints behave as normal, 'disabled' means that VM doesn't stop on breakpoints.
   * It doesn't update individual properties of [Breakpoint]s. Method call
   * with a null value and not null callback simply returns current value.
   */
  fun enableBreakpoints(enabled: Boolean): Promise<*>

  fun setBreakOnFirstStatement()

  fun isBreakOnFirstStatement(context: SuspendContext<*>): Boolean

  interface SetBreakpointResult
  data class BreakpointExist(val existingBreakpoint: Breakpoint) : SetBreakpointResult
  data class BreakpointCreated(val breakpoint: Breakpoint, val isRegistered: Promise<out Breakpoint>) : SetBreakpointResult
}

interface BreakpointListener : EventListener {
  fun resolved(breakpoint: Breakpoint)

  fun errorOccurred(breakpoint: Breakpoint, errorMessage: String?)

  fun nonProvisionalBreakpointRemoved(breakpoint: Breakpoint) {
  }
}