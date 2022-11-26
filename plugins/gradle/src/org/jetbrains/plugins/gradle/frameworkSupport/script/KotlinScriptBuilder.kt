// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression.ListElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.PropertyElement

class KotlinScriptBuilder(indent: Int = 0) : AbstractScriptBuilder(indent) {
  override fun add(element: ScriptElement, indent: Int, isNewLine: Boolean) {
    when {
      element is ListElement -> {
        add("listOf(", indent, isNewLine)
        add(element.elements, indent)
        add(")", indent, false)
        return
      }
      element is PropertyElement -> {
        add("var", indent, isNewLine)
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