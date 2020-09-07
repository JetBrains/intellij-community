// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger.frame

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XValueChildrenList
import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.*

// isInLibraryContent call could be costly, so we compute it only once (our customizePresentation called on each repaint)
class CallFrameView @JvmOverloads constructor(val callFrame: CallFrame,
                                              override val viewSupport: DebuggerViewSupport,
                                              val script: Script? = null,
                                              sourceInfo: SourceInfo? = null,
                                              isInLibraryContent: Boolean? = null,
                                              override val vm: Vm? = null,
                                              val methodReturnValue: Variable? = null) : XStackFrame(), VariableContext {
  private val sourceInfo = sourceInfo ?: viewSupport.getSourceInfo(script, callFrame)
  private val isInLibraryContent: Boolean = isInLibraryContent ?: (this.sourceInfo != null && viewSupport.isInLibraryContent(this.sourceInfo, script))

  private var evaluator: XDebuggerEvaluator? = null

  override fun getEqualityObject(): Any = callFrame.equalityObject

  override fun computeChildren(node: XCompositeNode) {
    node.setAlreadySorted(true)
    methodReturnValue?.let {
      val list = XValueChildrenList.singleton(VariableView(methodReturnValue, this))
      node.addChildren(list, false)
    }

    createAndAddScopeList(node, callFrame.variableScopes, this, callFrame)
  }

  override val evaluateContext: EvaluateContext
    get() = callFrame.evaluateContext

  override fun watchableAsEvaluationExpression(): Boolean = true

  override val memberFilter: Promise<MemberFilter>
    get() = viewSupport.getMemberFilter(this)

  fun getMemberFilter(scope: Scope): Promise<MemberFilter> = createVariableContext(scope, this, callFrame).memberFilter

  override fun getEvaluator(): XDebuggerEvaluator? {
    if (evaluator == null) {
      evaluator = viewSupport.createFrameEvaluator(this)
    }
    return evaluator
  }

  override fun getSourcePosition(): SourceInfo? = sourceInfo

  override fun customizePresentation(component: ColoredTextContainer) {
    if (sourceInfo == null) {
      val scriptName = if (script == null) XDebuggerBundle.message("stack.frame.function.unknown") else script.url.trimParameters().toDecodedForm()
      val line = callFrame.line
      component.append(if (line == -1) scriptName else "$scriptName:$line", SimpleTextAttributes.ERROR_ATTRIBUTES)
      return
    }

    val fileName = sourceInfo.file.name
    val line = sourceInfo.line + 1

    val textAttributes =
        if (isInLibraryContent || callFrame.isFromAsyncStack) SimpleTextAttributes.GRAYED_ATTRIBUTES
        else SimpleTextAttributes.REGULAR_ATTRIBUTES

    @NlsSafe val functionName = sourceInfo.functionName
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
        component.append(XDebuggerBundle.message("stack.frame.function.name.anonymous"), if (isInLibraryContent) SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES else SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES)
      }
      else {
        component.append(functionName, textAttributes)
      }
      component.append("(), $fileName:$line", textAttributes)
    }
    component.setIcon(AllIcons.Debugger.Frame)
  }
}