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

import com.intellij.openapi.util.NotNullLazyValue

const val RECEIVER_NAME = "this"

@Deprecated("")
/**
 * Use kotlin - base class is not required in this case (no boilerplate code)
 */
/**
 * You must initialize [.scopes] or override [.getVariableScopes]
 */
abstract class CallFrameBase(override val functionName: String?, override val line: Int, override val column: Int, override val evaluateContext: EvaluateContext) : CallFrame {
  protected var scopes: NotNullLazyValue<List<Scope>>? = null

  override var hasOnlyGlobalScope: Boolean = false
    protected set(value: Boolean) {
      field = value
    }

  override val variableScopes: List<Scope>
    get() = scopes!!.value

  override val asyncFunctionName: String? = null

  override val isFromAsyncStack: Boolean = false
}