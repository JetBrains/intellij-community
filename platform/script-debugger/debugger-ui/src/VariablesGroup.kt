/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.xdebugger.frame.XValueGroup

internal class VariablesGroup(private val start: Int, private val end: Int, private val variables: List<Variable>, private val context: VariableContext, name: String) : XValueGroup(name) {
  constructor(name: String, variables: List<Variable>, context: VariableContext) : this(0, variables.size, variables, context, name) {
  }

  override fun computeChildren(node: XCompositeNode) {
    node.setAlreadySorted(true)
    node.addChildren(createVariablesList(variables, start, end, context, null), true)
  }
}

internal fun createArrayRangeGroup(variables: List<Variable>, start: Int, end: Int, variableContext: VariableContext): VariablesGroup {
  val name = "[${variables[start].name} \u2026 ${variables[end - 1].name}]"
  return VariablesGroup(start, end, variables, variableContext, name)
}
