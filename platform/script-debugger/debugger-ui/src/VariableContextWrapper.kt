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

import com.intellij.openapi.util.AtomicNotNullLazyValue
import org.jetbrains.concurrency.Promise

internal open class VariableContextWrapper(override val parent: VariableContext, override val scope: Scope?) : VariableContext {
  // it's worth to cache it (JavaScriptDebuggerViewSupport, for example, performs expensive computation)
  private val memberFilterPromise = object : AtomicNotNullLazyValue<Promise<MemberFilter>>() {
    override fun compute() = parent.viewSupport.getMemberFilter(this@VariableContextWrapper)
  }

  override val variableName: String?
    get() = parent.variableName

  override val memberFilter: Promise<MemberFilter>
    get() = memberFilterPromise.value

  override val evaluateContext: EvaluateContext
    get() = parent.evaluateContext

  override val viewSupport: DebuggerViewSupport
    get() = parent.viewSupport

  override val vm: Vm?
    get() = parent.vm

  override fun watchableAsEvaluationExpression() = parent.watchableAsEvaluationExpression()
}