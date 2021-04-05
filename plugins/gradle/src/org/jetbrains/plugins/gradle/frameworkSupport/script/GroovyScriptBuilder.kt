// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.ArgumentElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression.CallElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression.StringElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder.Companion.script

class GroovyScriptBuilder : AbstractScriptBuilder() {
  override fun add(element: ScriptElement, indent: Int, isNewLine: Boolean) {
    when {
      element is ArgumentElement && element.name != null -> {
        add(element.name, indent, isNewLine)
        add(": ", indent, false)
        add(element.value, indent, false)
        return
      }
      element is CallElement && isNewLine && element.arguments.isNotEmpty() && !hasTrailingBlock(element.arguments) -> {
        add(element.name, indent, isNewLine)
        add(" ", indent, false)
        add(element.arguments, indent)
        return
      }
      element is StringElement && !element.value.contains('$') -> {
        add("'${element.value}'", indent, isNewLine)
        return
      }
    }
    super.add(element, indent, isNewLine)
  }

  companion object {
    fun groovy(configure: ScriptTreeBuilder.() -> Unit) =
      script(GroovyScriptBuilder(), configure)
  }
}