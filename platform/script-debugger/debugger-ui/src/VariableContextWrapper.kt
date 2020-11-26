// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger

import com.intellij.openapi.util.NotNullLazyValue
import org.jetbrains.concurrency.Promise

internal open class VariableContextWrapper(override val parent: VariableContext, override val scope: Scope?) : VariableContext {
  // it's worth to cache it (JavaScriptDebuggerViewSupport, for example, performs expensive computation)
  private val memberFilterPromise = NotNullLazyValue.atomicLazy {
    parent.viewSupport.getMemberFilter(this@VariableContextWrapper)
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