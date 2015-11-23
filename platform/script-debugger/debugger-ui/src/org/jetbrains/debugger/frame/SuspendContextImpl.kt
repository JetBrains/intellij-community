/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.debugger.frame

import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XSuspendContext
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.rejectedPromise
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.concurrency.thenAsync
import org.jetbrains.debugger.*
import org.jetbrains.debugger.values.StringValue

open class SuspendContextImpl(suspendContext: SuspendContext<out CallFrame>, debugProcess: DebuggerViewSupport, topFrameScript: Script?, topFrameSourceInfo: SourceInfo? = null) : XSuspendContext() {
  private val executionStack = ExecutionStackImpl(suspendContext, debugProcess, topFrameScript, topFrameSourceInfo)

  override final fun getActiveExecutionStack(): XExecutionStack = executionStack

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