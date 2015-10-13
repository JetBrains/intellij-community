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

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XStackFrame
import org.jetbrains.debugger.*

class CallFrameView(val callFrame: CallFrame,
                    private val sourceInfo: SourceInfo?,
                    private val viewSupport: DebuggerViewSupport,
                    val script: Script?) : XStackFrame(), VariableContext {
  // isInLibraryContent call could be costly, so we compute it only once (our customizePresentation called on each repaint)
  private val inLibraryContent = sourceInfo != null && viewSupport.isInLibraryContent(sourceInfo, script)
  private var evaluator: XDebuggerEvaluator? = null

  constructor(callFrame: CallFrame, viewSupport: DebuggerViewSupport, script: Script?) : this(callFrame, viewSupport.getSourceInfo(script, callFrame), viewSupport, script) {
  }

  override fun getEqualityObject() = callFrame.equalityObject

  override fun computeChildren(node: XCompositeNode) {
    node.setAlreadySorted(true)
    createAndAddScopeList(node, callFrame.variableScopes, this, callFrame)
  }

  override fun getEvaluateContext() = callFrame.evaluateContext

  override fun getName() = null

  override fun getParent() = null

  override fun watchableAsEvaluationExpression() = true

  override fun getViewSupport() = viewSupport

  override fun getMemberFilter() = viewSupport.getMemberFilter(this)

  fun getMemberFilter(scope: Scope) = createVariableContext(scope, this, callFrame).memberFilter

  override fun getScope() = null

  override fun getEvaluator(): XDebuggerEvaluator? {
    if (evaluator == null) {
      evaluator = viewSupport.createFrameEvaluator(this)
    }
    return evaluator
  }

  override fun getSourcePosition() = sourceInfo

  override fun customizePresentation(component: ColoredTextContainer) {
    if (sourceInfo == null) {
      val scriptName = if (script == null) "unknown" else script.url.trimParameters().toDecodedForm()
      val line = callFrame.line
      component.append(if (line != -1) scriptName + ':' + line else scriptName, SimpleTextAttributes.ERROR_ATTRIBUTES)
      return
    }

    val fileName = sourceInfo.file.name
    val line = sourceInfo.line + 1

    val textAttributes = if (inLibraryContent) SimpleTextAttributes.GRAYED_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES

    val functionName = sourceInfo.functionName
    if (functionName == null || (functionName.isEmpty() && callFrame.hasOnlyGlobalScope())) {
      component.append(fileName + ":" + line, textAttributes)
    }
    else {
      if (functionName.isEmpty()) {
        component.append("anonymous", if (inLibraryContent) SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES else SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES)
      }
      else {
        component.append(functionName, textAttributes)
      }
      component.append("(), $fileName:$line", textAttributes)
    }
    component.setIcon(AllIcons.Debugger.StackFrame)
  }
}