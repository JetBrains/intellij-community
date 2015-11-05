package org.jetbrains.debugger.frame

import com.intellij.xdebugger.frame.XSuspendContext
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.rejectedPromise
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.concurrency.thenAsync
import org.jetbrains.debugger.DebuggerViewSupport
import org.jetbrains.debugger.EvaluateContext
import org.jetbrains.debugger.Script
import org.jetbrains.debugger.SuspendContext
import org.jetbrains.debugger.values.StringValue

open class SuspendContextImpl(private val suspendContext: SuspendContext, debugProcess: DebuggerViewSupport, topFrameScript: Script?) : XSuspendContext() {
  private val executionStack = ExecutionStackImpl(suspendContext, debugProcess, topFrameScript)

  override fun getActiveExecutionStack() = executionStack

  fun evaluateExpression(expression: String): Promise<String> {
    val frame = executionStack.topFrame ?: return rejectedPromise("Top frame is null")
    return evaluateExpression(frame.callFrame.evaluateContext, expression)
  }

  private fun evaluateExpression(evaluateContext: EvaluateContext, expression: String) = evaluateContext.evaluate(expression)
    .thenAsync {
      val value = it.value ?: return@thenAsync resolvedPromise("Log expression result doesn't have value")
      if (value is StringValue && value.isTruncated) {
        value.fullString
      }
      else {
        resolvedPromise(value.valueString!!)
      }
    }
}