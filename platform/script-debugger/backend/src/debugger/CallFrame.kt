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

interface CallFrame {
  /**
   * @return the scopes known in this frame
   */
  val variableScopes: List<Scope>

  val hasOnlyGlobalScope: Boolean

  /**
   * receiver variable known in this frame ("this" variable)
   * Computed variable must be null if no receiver variable
   */
  val receiverVariable: Promise<Variable?>

  val line: Int

  val column: Int

  /**
   * @return the name of the current function of this frame
   */
  val functionName: String?

  /**
   * @return context for evaluating expressions in scope of this frame
   */
  val evaluateContext: EvaluateContext

  /**
   * @see com.intellij.xdebugger.frame.XStackFrame.getEqualityObject
   */
  val equalityObject: Any

  /**
   * Name of function which scheduled some handler for top frames of async stack.
   */
  val asyncFunctionName: String?

  val isFromAsyncStack: Boolean
}