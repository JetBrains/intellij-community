// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression.ListElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression.StringElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.PropertyElement

class KotlinDslGradleScriptBuilder(indent: Int = 0) : AbstractGradleScriptBuilder(indent) {

  override fun addListElement(element: ListElement, indent: Int, isNewLine: Boolean) {
    add("listOf(", indent, isNewLine)
    add(element.elements, indent)
    add(")", indent, false)
  }

  override fun addPropertyElement(element: PropertyElement, indent: Int, isNewLine: Boolean) {
    add("var", indent, isNewLine)
    add(" ", indent, false)
    add(element.name, indent, false)
    add(" = ", indent, false)
    add(element.value, indent, false)
  }

  override fun addStringElement(element: StringElement, indent: Int, isNewLine: Boolean) {
    val escapedString = element.value
      .replace("\\", "\\\\")
      .replace("\n", "\\n")
    val string = "\"" + escapedString.replace("\"", "\\\"") + "\""
    add(string, indent, isNewLine)
  }
}