// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger

import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueGroup
import org.jetbrains.concurrency.onError
import org.jetbrains.concurrency.onSuccess
import org.jetbrains.concurrency.thenAsyncAccept

class ScopeVariablesGroup(val scope: Scope, parentContext: VariableContext, callFrame: CallFrame?) : XValueGroup(scope.createScopeNodeName()) {
  private val context = createVariableContext(scope, parentContext, callFrame)

  private val callFrame = if (scope.type == ScopeType.LOCAL) callFrame else null

  override fun isAutoExpand(): Boolean = scope.type == ScopeType.LOCAL || scope.type == ScopeType.CATCH

  override fun getComment(): String? {
    val className = scope.description
    return if ("Object" == className) null else className
  }

  override fun computeChildren(node: XCompositeNode) {
    val promise = processScopeVariables(scope, node, context, callFrame == null)
    if (callFrame == null) {
      return
    }

    promise
      .onSuccess(node) {
        context.memberFilter
          .thenAsyncAccept(node) {
            if (it.hasNameMappings()) {
              it.sourceNameToRaw(RECEIVER_NAME)?.let {
                return@thenAsyncAccept callFrame.evaluateContext.evaluate(it)
                  .onSuccess(node) {
                    VariableImpl(RECEIVER_NAME, it.value, null)
                    node.addChildren(XValueChildrenList.singleton(VariableView(
                      VariableImpl(RECEIVER_NAME, it.value, null), context)), true)
                  }
              }
            }

            context.viewSupport.computeReceiverVariable(context, callFrame, node)
          }
          .onError(node) {
            context.viewSupport.computeReceiverVariable(context, callFrame, node)
          }
      }
  }
}

fun createAndAddScopeList(node: XCompositeNode, scopes: List<Scope>, context: VariableContext, callFrame: CallFrame?) {
  val list = XValueChildrenList(scopes.size)
  for (scope in scopes) {
    list.addTopGroup(ScopeVariablesGroup(scope, context, callFrame))
  }
  node.addChildren(list, true)
}

fun createVariableContext(scope: Scope, parentContext: VariableContext, callFrame: CallFrame?): VariableContext {
  if (callFrame == null || scope.type == ScopeType.LIBRARY) {
    // functions scopes - we can watch variables only from global scope
    return ParentlessVariableContext(parentContext, scope, scope.type == ScopeType.GLOBAL)
  }
  else {
    return VariableContextWrapper(parentContext, scope)
  }
}

private class ParentlessVariableContext(parentContext: VariableContext, scope: Scope, private val watchableAsEvaluationExpression: Boolean) : VariableContextWrapper(parentContext, scope) {
  override fun watchableAsEvaluationExpression() = watchableAsEvaluationExpression
}

private fun Scope.createScopeNodeName(): String {
  when (type) {
    ScopeType.GLOBAL -> return XDebuggerBundle.message("scope.global")
    ScopeType.LOCAL -> return XDebuggerBundle.message("scope.local")
    ScopeType.WITH -> return XDebuggerBundle.message("scope.with")
    ScopeType.CLOSURE -> return XDebuggerBundle.message("scope.closure")
    ScopeType.CATCH -> return XDebuggerBundle.message("scope.catch")
    ScopeType.LIBRARY -> return XDebuggerBundle.message("scope.library")
    ScopeType.INSTANCE -> return XDebuggerBundle.message("scope.instance")
    ScopeType.CLASS -> return XDebuggerBundle.message("scope.class")
    ScopeType.BLOCK -> return XDebuggerBundle.message("scope.block")
    ScopeType.SCRIPT -> return XDebuggerBundle.message("scope.script")
    ScopeType.UNKNOWN -> return XDebuggerBundle.message("scope.unknown")
    else -> throw IllegalArgumentException(type.name)
  }
}