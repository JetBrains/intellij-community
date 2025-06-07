// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.completion.templates

import com.intellij.codeInsight.daemon.impl.quickfix.EmptyExpression
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.codeInsight.template.impl.TemplateImpl
import com.intellij.codeInsight.template.impl.Variable
import org.editorconfig.language.util.EditorConfigTemplateUtil.uniqueId

/**
 * Adds exactly one segment
 */
class EditorConfigTemplateSegmentBuildAssistant(
  private val template: TemplateImpl,
  private val cachedVariables: MutableMap<String, Variable>
) {
  private val nextTokens = mutableListOf<String>()
  private var variableId: String? = null

  fun saveLastVariableId(id: String) {
    variableId = id
  }

  fun addNextConstant(token: String) {
    nextTokens.add(token)
  }

  fun saveNextTokens() {
    val variable = addExpression()

    val variableId = variableId
    if (variableId != null && variable != null) {
      cachedVariables[variableId] = variable
    }

    nextTokens.clear()
    this.variableId = null
  }

  private fun addExpression(): Variable? {
    if (nextTokens.isEmpty()) {
      val variableId = variableId ?: return null
      val expression = EmptyExpression()
      val placeholder = ConstantNode(variableId).withLookupStrings(variableId)
      return template.addVariable(uniqueId, expression, placeholder, true)
    }

    val distinctTokes = nextTokens.distinct()

    if (variableId == null && distinctTokes.size == 1) {
      template.addTextSegment(distinctTokes.single())
      return null
    }

    val expression = ConstantNode(null).withLookupStrings(distinctTokes)
    val placeholderText = variableId ?: distinctTokes.first()
    val placeholder = ConstantNode(placeholderText).withLookupStrings(placeholderText)
    return template.addVariable(variableId ?: uniqueId, expression, placeholder, true)
  }
}
