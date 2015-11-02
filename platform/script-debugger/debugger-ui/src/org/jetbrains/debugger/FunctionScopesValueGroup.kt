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

import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueGroup
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.done
import org.jetbrains.debugger.values.FunctionValue
import org.jetbrains.rpc.LOG
import java.util.*

internal class FunctionScopesValueGroup(private val functionValue: FunctionValue, private val variableContext: VariableContext) : XValueGroup("Function scopes") {
  override fun computeChildren(node: XCompositeNode) {
    node.setAlreadySorted(true)

    functionValue.resolve()
      .done(node) {
          val scopes = it.scopes
          if (scopes == null || scopes.size == 0) {
            node.addChildren(XValueChildrenList.EMPTY, true)
          }
          else {
            createAndAddScopeList(node, Arrays.asList(*scopes), variableContext, null)
          }
        }
      .rejected {
        Promise.logError(LOG, it)
        node.setErrorMessage(it.message!!)
      }
  }
}