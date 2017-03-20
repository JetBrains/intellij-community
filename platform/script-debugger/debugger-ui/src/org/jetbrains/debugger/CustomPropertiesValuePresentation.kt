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

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import org.jetbrains.debugger.values.ObjectValue
import org.jetbrains.debugger.values.StringValue
import org.jetbrains.debugger.values.ValueType

class CustomPropertiesValuePresentation(private val value: ObjectValue, private val properties: List<Variable>) : XValuePresentation() {
  override fun renderValue(renderer: XValuePresentation.XValueTextRenderer) {
    renderer.renderComment(getObjectValueDescription(value))
    renderer.renderSpecialSymbol(" {")
    var isFirst = true
    for (property in properties) {
      if (isFirst) {
        isFirst = false
      }
      else {
        renderer.renderSpecialSymbol(", ")
      }

      renderer.renderValue(property.name, DefaultLanguageHighlighterColors.INSTANCE_FIELD)
      renderer.renderSpecialSymbol(": ")

      val value = property.value!!
      when (value.type) {
        ValueType.BOOLEAN, ValueType.NULL, ValueType.UNDEFINED, ValueType.SYMBOL -> renderer.renderKeywordValue(value.valueString!!)

        ValueType.NUMBER -> renderer.renderNumericValue(value.valueString!!)

        ValueType.STRING -> {
          val string = value.valueString
          renderer.renderStringValue(string!!, "\"\\", XValueNode.MAX_VALUE_LENGTH)
          val actualStringLength = (value as? StringValue)?.length ?: string.length
          if (actualStringLength > XValueNode.MAX_VALUE_LENGTH) {
            renderer.renderComment(XDebuggerBundle.message("node.text.ellipsis.truncated", actualStringLength))
          }
        }

        ValueType.FUNCTION -> renderer.renderComment(trimFunctionDescription(value))

        ValueType.OBJECT -> renderer.renderComment(getObjectValueDescription(value as ObjectValue))

        else -> renderer.renderValue(value.valueString!!)
      }
    }
    renderer.renderSpecialSymbol("}")
  }
}