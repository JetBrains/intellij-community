// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.ArgumentElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression.*
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.PropertyElement

class GroovyScriptBuilder(indent: Int = 0) : AbstractScriptBuilder(indent) {
  override fun add(element: ScriptElement, indent: Int, isNewLine: Boolean) {
    when {
      element is ArgumentElement && element.name != null -> {
        add(element.name, indent, isNewLine)
        add(": ", indent, false)
        add(element.value, indent, false)
      }
      element is CallElement && isNewLine && element.arguments.isNotEmpty() && !hasTrailingBlock(element.arguments) -> {
        add(element.name, indent, isNewLine = true)
        add(" ", indent, false)
        add(element.arguments, indent)
      }
      element is StringElement -> {
        val escapedString = element.value
          .replace("\\", "\\\\")
          .replace("\n", "\\n")
        val string = when ('$' in element.value) {
          true -> "\"" + escapedString.replace("\"", "\\\"") + "\""
          else -> "\'" + escapedString.replace("\'", "\\\'") + "\'"
        }
        add(string, indent, isNewLine)
      }
      element is ListElement -> {
        add("[", indent, isNewLine)
        add(element.elements, indent)
        add("]", indent, false)
      }
      element is PropertyElement -> {
        add("def", indent, isNewLine)
        add(" ", indent, false)
        add(element.name, indent, false)
        add(" = ", indent, false)
        add(element.value, indent, false)
      }
      else -> {
        super.add(element, indent, isNewLine)
      }
    }
  }
}