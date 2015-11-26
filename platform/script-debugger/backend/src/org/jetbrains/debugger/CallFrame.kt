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
}