// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.ArgumentElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.*
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression.*
import java.util.*

abstract class AbstractScriptBuilder(private val indent: Int = 0) : ScriptBuilder {
  private val lines = ArrayList<String>()

  override fun generate(root: BlockElement): String {
    lines.clear()
    root.statements.forEach { add(it, indent, true) }
    val joiner = StringJoiner("\n")
    lines.forEach(joiner::add)
    return joiner.toString()
  }

  protected open fun add(element: ScriptElement, indent: Int, isNewLine: Boolean) {
    when (element) {
      is ArgumentElement -> {
        if (element.name != null) {
          add(element.name, indent, isNewLine)
          add(" = ", indent, false)
        }
        add(element.value, indent, false)
      }
      is AssignElement -> {
        add(element.left, indent, isNewLine)
        add(" = ", indent, false)
        add(element.right, indent, false)
      }
      is PlusAssignElement -> {
        add(element.name, indent, isNewLine)
        add(" += ", indent, false)
        add(element.value, indent, false)
      }
      is BlockElement -> {
        add("{", indent, isNewLine)
        for (statement in element.statements) {
          add(statement, indent + 1, true)
        }
        add("}", indent, true)
      }
      is CallElement -> {
        add(element.name, indent, isNewLine)
        if (hasTrailingBlock(element.arguments)) {
          if (element.arguments.size > 1) {
            add("(", indent, false)
            add(element.arguments.dropLast(1), indent)
            add(")", indent, false)
          }
          add(" ", indent, false)
          add(element.arguments.last().value, indent, false)
        }
        else {
          add("(", indent, false)
          add(element.arguments, indent)
          add(")", indent, false)
        }
      }
      is CodeElement -> {
        for (line in element.text) {
          add(line, indent, isNewLine)
        }
      }
      is InfixCall -> {
        add(element.left, indent, isNewLine)
        add(" ", indent, false)
        add(element.name, indent, false)
        add(" ", indent, false)
        add(element.right, indent, false)
      }
      is IntElement -> {
        add(element.value.toString(), indent, isNewLine)
      }
      is BooleanElement -> {
        add(element.value.toString(), indent, isNewLine)
      }
      is StringElement -> {
        add(""""${element.value}"""", indent, isNewLine)
      }
      NewLineElement -> {
        add("", indent, isNewLine)
      }
      else -> {
        error("Unsupported element: ${element.javaClass}")
      }
    }
  }

  protected fun add(elements: List<ScriptElement>, indent: Int) {
    for ((i, argument) in elements.withIndex()) {
      if (i != 0) {
        add(", ", indent, false)
      }
      add(argument, indent, false)
    }
  }

  protected fun add(code: String, indent: Int, isNewLine: Boolean) {
    if (isNewLine || lines.isEmpty()) {
      if (code.isBlank()) {
        lines.add(code)
      }
      else {
        lines.add("    ".repeat(indent) + code)
      }
    }
    else {
      lines[lines.lastIndex] += code
    }
  }

  protected fun hasTrailingBlock(arguments: List<ArgumentElement>): Boolean {
    val last = arguments.lastOrNull() ?: return false
    return last.value is BlockElement && last.name == null
  }
}