// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger

import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueNode
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.onError
import org.jetbrains.concurrency.onSuccess
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.debugger.values.ObjectValue
import org.jetbrains.debugger.values.Value
import javax.swing.Icon

open class BasicDebuggerViewSupport : MemberFilter, DebuggerViewSupport {
  protected val defaultMemberFilterPromise: Promise<MemberFilter> = resolvedPromise<MemberFilter>(this)

  override fun propertyNamesToString(list: List<String>, quotedAware: Boolean): String = ValueModifierUtil.propertyNamesToString(list, quotedAware)

  override fun computeObjectPresentation(value: ObjectValue, variable: Variable, context: VariableContext, node: XValueNode, icon: Icon): Unit = VariableView.setObjectPresentation(value, icon, node)

  override fun computeArrayPresentation(value: Value, variable: Variable, context: VariableContext, node: XValueNode, icon: Icon) {
    VariableView.setArrayPresentation(value, context, icon, node)
  }

  override fun getMemberFilter(context: VariableContext): Promise<MemberFilter> = defaultMemberFilterPromise

  override fun computeReceiverVariable(context: VariableContext, callFrame: CallFrame, node: XCompositeNode): Promise<*> {
    return callFrame.receiverVariable
        .onSuccess(node) {
          node.addChildren(if (it == null) XValueChildrenList.EMPTY else XValueChildrenList.singleton(VariableView(it, context)), true)
        }
        .onError(node) {
          node.addChildren(XValueChildrenList.EMPTY, true)
        }
  }
}

interface PresentationProvider {
  fun computePresentation(node: XValueNode, icon: Icon): Boolean
}