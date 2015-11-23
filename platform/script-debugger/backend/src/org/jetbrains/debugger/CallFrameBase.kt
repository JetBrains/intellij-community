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
}