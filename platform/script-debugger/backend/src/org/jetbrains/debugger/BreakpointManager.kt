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

import java.util.EventListener

public interface BreakpointManager {
  public enum class MUTE_MODE {
    ALL,
    ONE,
    NONE
  }

  public val breakpoints: Iterable<Breakpoint>

  public val regExpBreakpointSupported: Boolean
    get() = false

  public fun setBreakpoint(target: BreakpointTarget, line: Int, column: Int, condition: String?, ignoreCount: Int, enabled: Boolean): Breakpoint

  public fun remove(breakpoint: Breakpoint): Promise<*>

  public val functionSupport: FunctionSupport?
    get() = null

  // Could be called multiple times for breakpoint
  public fun addBreakpointListener(listener: BreakpointListener)

  public fun removeAll(): Promise<*>

  public fun getMuteMode(): MUTE_MODE = BreakpointManager.MUTE_MODE.ONE

  /**
   * Flushes the breakpoint parameter changes (set* methods) into the browser
   * and invokes the callback once the operation has finished. This method must
   * be called for the set* method invocations to take effect.

   */
  public fun flush(breakpoint: Breakpoint): Promise<*>

  /**
   * Asynchronously enables or disables all breakpoints on remote. 'Enabled' means that
   * breakpoints behave as normal, 'disabled' means that VM doesn't stop on breakpoints.
   * It doesn't update individual properties of [Breakpoint]s. Method call
   * with a null value and not null callback simply returns current value.
   */
  public fun enableBreakpoints(enabled: Boolean): Promise<*>

  public interface BreakpointListener : EventListener {
    public fun resolved(breakpoint: Breakpoint)

    public fun errorOccurred(breakpoint: Breakpoint, errorMessage: String?)
  }
}