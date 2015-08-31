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

/**
 * A breakpoint in the browser JavaScript virtual machine. The `set*`
 * method invocations will not take effect until
 * [.flush] is called.
 */
public interface Breakpoint {
  companion object {
    /**
     * This value is used when the corresponding parameter is absent
     */
    public val EMPTY_VALUE: Int = -1

    /**
     * A breakpoint has this ID if it does not reflect an actual breakpoint in a
     * JavaScript VM debugger.
     */
    public val INVALID_ID: Int = -1
  }

  public val target: BreakpointTarget

  public val line: Int

  /**
   * @return whether this breakpoint is enabled
   */
  /**
   * Sets whether this breakpoint is enabled.
   * Requires subsequent [.flush] call.
   */
  public var enabled: Boolean

  /**
   * Sets the breakpoint condition as plain JavaScript (`null` to clear).
   * Requires subsequent [.flush] call.
   * @param condition the new breakpoint condition
   */
  public var condition: String?

  public val isResolved: Boolean

  /**
   * Be aware! V8 doesn't provide reliable debugger API, so, sometimes actual locations is empty - in this case this methods return "true".
   * V8 debugger doesn't report about resolved breakpoint if it is happened after initial breakpoint set. So, you cannot trust "actual locations".
   */
  public open fun isActualLineCorrect(): Boolean = true
}

/**
 * Visitor interface that includes all extensions.
 */
public interface TargetExtendedVisitor<R> : FunctionSupport.Visitor<R>, ScriptRegExpSupportVisitor<R>
