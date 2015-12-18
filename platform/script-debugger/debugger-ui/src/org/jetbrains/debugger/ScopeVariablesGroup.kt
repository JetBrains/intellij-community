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

import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueGroup
import org.jetbrains.concurrency.done
import org.jetbrains.concurrency.rejected

class ScopeVariablesGroup(val scope: Scope, parentContext: VariableContext, callFrame: CallFrame?) : XValueGroup(scope.createScopeNodeName()) {
  private val context = createVariableContext(scope, parentContext, callFrame)

  private val callFrame = if (scope.type == Scope.Type.LOCAL) callFrame else null

  override fun isAutoExpand() = scope.type == Scope.Type.LOCAL || scope.type == Scope.Type.CATCH

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
      .done(node) {
        callFrame.receiverVariable
          .done(node) {
            node.addChildren(if (it == null) XValueChildrenList.EMPTY else XValueChildrenList.singleton(VariableView(it, context)), true)
          }
          .rejected(node) {
            node.addChildren(XValueChildrenList.EMPTY, true)
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
  if (callFrame == null || scope.type == Scope.Type.LIBRARY) {
    // functions scopes - we can watch variables only from global scope
    return ParentlessVariableContext(parentContext, scope, scope.type == Scope.Type.GLOBAL)
  }
  else {
    return VariableContextWrapper(parentContext, scope)
  }
}

private class ParentlessVariableContext(parentContext: VariableContext, scope: Scope, private val watchableAsEvaluationExpression: Boolean) : VariableContextWrapper(parentContext, scope) {
  override fun watchableAsEvaluationExpression() = watchableAsEvaluationExpression

  override fun getParent() = null
}

private fun Scope.createScopeNodeName(): String {
  when (type) {
    Scope.Type.GLOBAL -> return XDebuggerBundle.message("scope.global")
    Scope.Type.LOCAL -> return XDebuggerBundle.message("scope.local")
    Scope.Type.WITH -> return XDebuggerBundle.message("scope.with")
    Scope.Type.CLOSURE -> return XDebuggerBundle.message("scope.closure")
    Scope.Type.CATCH -> return XDebuggerBundle.message("scope.catch")
    Scope.Type.LIBRARY -> return XDebuggerBundle.message("scope.library")
    Scope.Type.INSTANCE -> return XDebuggerBundle.message("scope.instance")
    Scope.Type.CLASS -> return XDebuggerBundle.message("scope.class")
    Scope.Type.BLOCK -> return XDebuggerBundle.message("scope.block")
    Scope.Type.SCRIPT -> return XDebuggerBundle.message("scope.script")
    Scope.Type.UNKNOWN -> return XDebuggerBundle.message("scope.unknown")
    else -> throw IllegalArgumentException(type.name)
  }
}