// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.ArgumentElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression.*
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.PropertyElement

class GroovyScriptBuilder(indent: Int = 0) : AbstractScriptBuilder(indent) {

  override fun addArgumentElement(element: ArgumentElement, indent: Int, isNewLine: Boolean) {
    if (element.name != null) {
      add(element.name, indent, isNewLine)
      add(": ", indent, false)
      add(element.value, indent, false)
      return
    }
    super.addArgumentElement(element, indent, isNewLine)
  }

  override fun addCallElement(element: CallElement, indent: Int, isNewLine: Boolean) {
    if (isNewLine && element.arguments.isNotEmpty() && !hasTrailingBlock(element.arguments)) {
      add(element.name, indent, isNewLine = true)
      add(" ", indent, false)
      add(element.arguments, indent)
      return
    }
    super.addCallElement(element, indent, isNewLine)
  }

  override fun addStringElement(element: StringElement, indent: Int, isNewLine: Boolean) {
    val escapedString = element.value
      .replace("\\", "\\\\")
      .replace("\n", "\\n")
    val string = when ('$' in element.value) {
      true -> "\"" + escapedString.replace("\"", "\\\"") + "\""
      else -> "\'" + escapedString.replace("\'", "\\\'") + "\'"
    }
    add(string, indent, isNewLine)
  }

  override fun addListElement(element: ListElement, indent: Int, isNewLine: Boolean) {
    add("[", indent, isNewLine)
    add(element.elements, indent)
    add("]", indent, false)
  }

  override fun addPropertyElement(element: PropertyElement, indent: Int, isNewLine: Boolean) {
    add("def", indent, isNewLine)
    add(" ", indent, false)
    add(element.name, indent, false)
    add(" = ", indent, false)
    add(element.value, indent, false)
  }
}