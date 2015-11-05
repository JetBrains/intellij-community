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

import com.intellij.util.ThreeState
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XInlineDebuggerDataCallback
import com.intellij.xdebugger.frame.XNavigatable
import com.intellij.xdebugger.frame.XValueNode
import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.frame.CallFrameView
import org.jetbrains.debugger.values.ObjectValue
import org.jetbrains.debugger.values.Value
import org.jetbrains.rpc.LOG
import javax.swing.Icon

interface DebuggerViewSupport {
  val vm: Vm?
    get() = null

  fun getSourceInfo(script: Script?, frame: CallFrame): SourceInfo? = null

  fun getSourceInfo(functionName: String?, scriptUrl: String, line: Int, column: Int): SourceInfo? = null

  fun getSourceInfo(functionName: String?, script: Script, line: Int, column: Int): SourceInfo? = null

  fun propertyNamesToString(list: List<String>, quotedAware: Boolean): String

  // Please, don't hesitate to ask to share some generic implementations. Don't reinvent the wheel and keep in mind - user expects the same UI across all IDEA-based IDEs.
  fun computeObjectPresentation(value: ObjectValue, variable: Variable, context: VariableContext, node: XValueNode, icon: Icon)

  fun computeArrayPresentation(value: Value, variable: Variable, context: VariableContext, node: XValueNode, icon: Icon)

  fun createFrameEvaluator(frame: CallFrameView): XDebuggerEvaluator = PromiseDebuggerEvaluator(frame)

  /**
   * [org.jetbrains.debugger.values.FunctionValue] is special case and handled by SDK
   */
  fun canNavigateToSource(variable: Variable, context: VariableContext) = false

  fun computeSourcePosition(name: String, value: Value?, variable: Variable, context: VariableContext, navigatable: XNavigatable) {
  }

  fun computeInlineDebuggerData(name: String, variable: Variable, context: VariableContext, callback: XInlineDebuggerDataCallback) = ThreeState.UNSURE

  // return null if you don't need to add additional properties
  fun computeAdditionalObjectProperties(value: ObjectValue, variable: Variable, context: VariableContext, node: XCompositeNode): Promise<*>? = null

  fun getMemberFilter(context: VariableContext): Promise<MemberFilter>

  fun transformErrorOnGetUsedReferenceValue(value: Value?, error: String?) = value

  fun isInLibraryContent(sourceInfo: SourceInfo, script: Script?) = false
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