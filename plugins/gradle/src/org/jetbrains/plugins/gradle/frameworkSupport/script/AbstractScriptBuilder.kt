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

  protected open fun addArgumentElement(element: ArgumentElement, indent: Int, isNewLine: Boolean) {
    if (element.name != null) {
      add(element.name, indent, isNewLine)
      add(" = ", indent, false)
    }
    add(element.value, indent, false)
  }

  protected abstract fun addPropertyElement(element: PropertyElement, indent: Int, isNewLine: Boolean)

  protected open fun addAssignElement(element: AssignElement, indent: Int, isNewLine: Boolean) {
    add(element.left, indent, isNewLine)
    add(" = ", indent, false)
    add(element.right, indent, false)
  }

  protected open fun addPlusAssignElement(element: PlusAssignElement, indent: Int, isNewLine: Boolean) {
    add(element.name, indent, isNewLine)
    add(" += ", indent, false)
    add(element.value, indent, false)
  }

  protected open fun addBlockElement(element: BlockElement, indent: Int, isNewLine: Boolean) {
    add("{", indent, isNewLine)
    for (statement in element.statements) {
      add(statement, indent + 1, true)
    }
    add("}", indent, true)
  }

  protected open fun addCallElement(element: CallElement, indent: Int, isNewLine: Boolean) {
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

  protected open fun addCodeElement(element: CodeElement, indent: Int, isNewLine: Boolean) {
    for (line in element.text) {
      add(line, indent, isNewLine)
    }
  }

  protected open fun addInfixCallElement(element: InfixCall, indent: Int, isNewLine: Boolean) {
    add(element.left, indent, isNewLine)
    add(" ", indent, false)
    add(element.name, indent, false)
    add(" ", indent, false)
    add(element.right, indent, false)
  }

  protected open fun addIntElement(element: IntElement, indent: Int, isNewLine: Boolean) {
    add(element.value.toString(), indent, isNewLine)
  }

  protected open fun addBooleanElement(element: BooleanElement, indent: Int, isNewLine: Boolean) {
    add(element.value.toString(), indent, isNewLine)
  }

  protected abstract fun addStringElement(element: StringElement, indent: Int, isNewLine: Boolean)

  protected abstract fun addListElement(element: ListElement, indent: Int, isNewLine: Boolean)

  protected open fun addNewLineElement(indent: Int, isNewLine: Boolean) {
    add("", indent, isNewLine)
  }

  protected fun add(element: ScriptElement, indent: Int, isNewLine: Boolean) {
    when (element) {
      is ArgumentElement -> addArgumentElement(element, indent, isNewLine)
      is NewLineElement -> addNewLineElement(indent, isNewLine)
      is PropertyElement -> addPropertyElement(element, indent, isNewLine)
      is AssignElement -> addAssignElement(element, indent, isNewLine)
      is PlusAssignElement -> addPlusAssignElement(element, indent, isNewLine)
      is IntElement -> addIntElement(element, indent, isNewLine)
      is BooleanElement -> addBooleanElement(element, indent, isNewLine)
      is StringElement -> addStringElement(element, indent, isNewLine)
      is ListElement -> addListElement(element, indent, isNewLine)
      is CodeElement -> addCodeElement(element, indent, isNewLine)
      is InfixCall -> addInfixCallElement(element, indent, isNewLine)
      is CallElement -> addCallElement(element, indent, isNewLine)
      is BlockElement -> addBlockElement(element, indent, isNewLine)
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