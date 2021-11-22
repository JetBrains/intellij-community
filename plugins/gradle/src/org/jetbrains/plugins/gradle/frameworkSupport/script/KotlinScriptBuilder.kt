// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression.ListElement

class KotlinScriptBuilder(indent: Int = 0) : AbstractScriptBuilder(indent) {
  override fun add(element: ScriptElement, indent: Int, isNewLine: Boolean) {
    if (element is ListElement) {
      add("listOf(", indent, isNewLine)
      add(element.elements, indent)
      add(")", indent, false)
      return
    }
    super.add(element, indent, isNewLine)
  }

  companion object {
    fun kotlin(configure: ScriptTreeBuilder.() -> Unit) =
      ScriptTreeBuilder.script(KotlinScriptBuilder(), configure)
  }
}