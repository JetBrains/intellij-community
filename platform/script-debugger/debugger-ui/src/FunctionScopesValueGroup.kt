// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger

import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueGroup
import org.jetbrains.concurrency.errorIfNotMessage
import org.jetbrains.concurrency.onSuccess
import org.jetbrains.debugger.values.FunctionValue
import org.jetbrains.rpc.LOG
import java.util.*

internal class FunctionScopesValueGroup(private val functionValue: FunctionValue, private val variableContext: VariableContext) : XValueGroup("Function scopes") {
  override fun computeChildren(node: XCompositeNode) {
    node.setAlreadySorted(true)

    functionValue.resolve()
      .onSuccess(node) {
          val scopes = it.scopes
          if (scopes == null || scopes.size == 0) {
            node.addChildren(XValueChildrenList.EMPTY, true)
          }
          else {
            createAndAddScopeList(node, Arrays.asList(*scopes), variableContext, null)
          }
        }
      .onError {
        LOG.errorIfNotMessage(it)
        node.setErrorMessage(it.message!!)
      }
  }
}