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

  override fun watchableAsEvaluationExpression() = parent.watchableAsEvaluationExpression()
}