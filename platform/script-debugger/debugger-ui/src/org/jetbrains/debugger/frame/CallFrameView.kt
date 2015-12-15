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

// isInLibraryContent call could be costly, so we compute it only once (our customizePresentation called on each repaint)
class CallFrameView @JvmOverloads constructor(val callFrame: CallFrame,
                                              private val viewSupport: DebuggerViewSupport,
                                              val script: Script? = null,
                                              sourceInfo: SourceInfo? = null,
                                              isInLibraryContent: Boolean? = null) : XStackFrame(), VariableContext {
  private val sourceInfo = sourceInfo ?: viewSupport.getSourceInfo(script, callFrame)
  private val isInLibraryContent: Boolean = isInLibraryContent ?: (this.sourceInfo != null && viewSupport.isInLibraryContent(this.sourceInfo, script))

  private var evaluator: XDebuggerEvaluator? = null

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
      component.append(if (line == -1) scriptName else "$scriptName:$line", SimpleTextAttributes.ERROR_ATTRIBUTES)
      return
    }

    val fileName = sourceInfo.file.name
    val line = sourceInfo.line + 1

    val textAttributes =
        if (isInLibraryContent || callFrame.isFromAsyncStack) SimpleTextAttributes.GRAYED_ATTRIBUTES
        else SimpleTextAttributes.REGULAR_ATTRIBUTES

    val functionName = sourceInfo.functionName
    if (functionName == null || (functionName.isEmpty() && callFrame.hasOnlyGlobalScope)) {
      if (fileName.startsWith("index.")) {
        sourceInfo.file.parent?.let {
          component.append("${it.name}/", textAttributes)
        }
      }
      component.append("$fileName:$line", textAttributes)
    }
    else {
      if (functionName.isEmpty()) {
        component.append("anonymous", if (isInLibraryContent) SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES else SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES)
      }
      else {
        component.append(functionName, textAttributes)
      }
      component.append("(), $fileName:$line", textAttributes)
    }
    component.setIcon(AllIcons.Debugger.StackFrame)
  }
}