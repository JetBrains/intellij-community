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
package org.jetbrains.debugger

import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValueNode
import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.frame.CallFrameView
import org.jetbrains.debugger.values.ObjectValue
import org.jetbrains.debugger.values.Value
import org.jetbrains.rpc.LOG
import javax.swing.Icon

open class BasicDebuggerViewSupport : MemberFilter, DebuggerViewSupport {
  protected val defaultMemberFilterPromise = Promise.resolve<MemberFilter>(this)

  override fun propertyNamesToString(list: List<String>, quotedAware: Boolean) = ValueModifierUtil.propertyNamesToString(list, quotedAware)

  override fun computeObjectPresentation(value: ObjectValue, variable: Variable, context: VariableContext, node: XValueNode, icon: Icon) = VariableView.setObjectPresentation(value, icon, node)

  override fun computeArrayPresentation(value: Value, variable: Variable, context: VariableContext, node: XValueNode, icon: Icon) {
    VariableView.setArrayPresentation(value, context, icon, node)
  }

  override fun createFrameEvaluator(frame: CallFrameView): XDebuggerEvaluator = PromiseDebuggerEvaluator(frame)

  override fun getMemberFilter(context: VariableContext) = defaultMemberFilterPromise
}

open class PromiseDebuggerEvaluator(protected val context: VariableContext) : XDebuggerEvaluator() {
  override final fun evaluate(expression: String, callback: XDebuggerEvaluator.XEvaluationCallback, expressionPosition: XSourcePosition?) {
    try {
      evaluate(expression, expressionPosition)
        .done { callback.evaluated(VariableView(VariableImpl(expression, it.value), context)) }
        .rejected { callback.errorOccurred(it.toString()) }
    }
    catch (e: Throwable) {
      LOG.error(e)
      callback.errorOccurred(e.toString())
      return
    }
  }

  protected open fun evaluate(expression: String, expressionPosition: XSourcePosition?): Promise<EvaluateResult> = context.evaluateContext.evaluate(expression)
}