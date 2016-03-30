/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

interface VariableContext {
  val evaluateContext: EvaluateContext

  /**
   * Parent variable name if this context is [org.jetbrains.debugger.VariableView]
   */
  val variableName: String?
    get() = null

  val parent: VariableContext?
    get() = null

  fun watchableAsEvaluationExpression(): Boolean

  val viewSupport: DebuggerViewSupport

  val memberFilter: Promise<MemberFilter>
    get() = viewSupport.getMemberFilter(this)

  val scope: Scope?
    get() = null

  val vm: Vm?
    get() = null
}