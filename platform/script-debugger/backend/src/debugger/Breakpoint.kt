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

/**
 * A breakpoint in the browser JavaScript virtual machine. The `set*`
 * method invocations will not take effect until
 * [.flush] is called.
 */
interface Breakpoint {
  companion object {
    /**
     * This value is used when the corresponding parameter is absent
     */
    const val EMPTY_VALUE = -1

    /**
     * A breakpoint has this ID if it does not reflect an actual breakpoint in a
     * JavaScript VM debugger.
     */
    const val INVALID_ID = -1
  }

  val target: BreakpointTarget

  val line: Int

  val column: Int

  /**
   * @return whether this breakpoint is enabled
   */
  /**
   * Sets whether this breakpoint is enabled.
   * Requires subsequent [.flush] call.
   */
  var enabled: Boolean

  /**
   * Sets the breakpoint condition as plain JavaScript (`null` to clear).
   * Requires subsequent [.flush] call.
   */
  var condition: String?

  val isResolved: Boolean

  /**
   * Be aware! V8 doesn't provide reliable debugger API, so, sometimes actual locations is empty - in this case this methods return "true".
   * V8 debugger doesn't report about resolved breakpoint if it is happened after initial breakpoint set. So, you cannot trust "actual locations".
   */
  fun isActualLineCorrect() = true
}

/**
 * Visitor interface that includes all extensions.
 */
interface TargetExtendedVisitor<R> : FunctionVisitor<R>, ScriptRegExpSupportVisitor<R>


/**
 * Additional interface that user visitor may implement for [BreakpointTarget.accept]
 * method.
 */
interface FunctionVisitor<R> : BreakpointTarget.Visitor<R> {
  fun visitFunction(expression: String): R
}