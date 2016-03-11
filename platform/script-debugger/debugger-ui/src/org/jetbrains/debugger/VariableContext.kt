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
}