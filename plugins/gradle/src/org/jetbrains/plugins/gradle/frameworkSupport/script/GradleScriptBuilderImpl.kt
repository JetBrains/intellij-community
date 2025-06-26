// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import org.jetbrains.plugins.gradle.frameworkSupport.GradleDsl
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.ArgumentElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.*
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression.*
import java.util.*
import kotlin.text.contains
import kotlin.text.replace

internal class GradleScriptBuilderImpl(
  override val gradleDsl: GradleDsl,
) : GradleScriptBuilder {

  private val lines = ArrayList<String>()

  override fun generate(root: BlockElement): String {
    lines.clear()
    root.statements.forEach { add(it, 0, true) }
    val joiner = StringJoiner("\n")
    lines.forEach(joiner::add)
    return joiner.toString()
  }

  private fun addArgumentElement(element: ArgumentElement, indent: Int, isNewLine: Boolean) {
    val argumentNameSeparator = when (gradleDsl) {
      GradleDsl.GROOVY -> ": "
      GradleDsl.KOTLIN -> " = "
    }
    if (element.name != null) {
      add(element.name, indent, isNewLine)
      add(argumentNameSeparator, indent, false)
    }
    add(element.value, indent, false)
  }

  private fun addPropertyElement(element: PropertyElement, indent: Int, isNewLine: Boolean) {
    val propertyDefinition = when (gradleDsl) {
      GradleDsl.GROOVY -> "def"
      GradleDsl.KOTLIN -> "var"
    }
    add(propertyDefinition, indent, isNewLine)
    add(" ", indent, false)
    add(element.name, indent, false)
    add(" = ", indent, false)
    add(element.value, indent, false)
  }

  private fun addAssignElement(element: AssignElement, indent: Int, isNewLine: Boolean) {
    add(element.left, indent, isNewLine)
    add(" = ", indent, false)
    add(element.right, indent, false)
  }

  private fun addPlusAssignElement(element: PlusAssignElement, indent: Int, isNewLine: Boolean) {
    add(element.name, indent, isNewLine)
    add(" += ", indent, false)
    add(element.value, indent, false)
  }

  private fun addBlockElement(element: BlockElement, indent: Int, isNewLine: Boolean) {
    add("{", indent, isNewLine)
    for (statement in element.statements) {
      add(statement, indent + 1, true)
    }
    add("}", indent, true)
  }

  private fun addCallElement(element: CallElement, indent: Int, isNewLine: Boolean) {
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
    else if (gradleDsl == GradleDsl.GROOVY && isNewLine && element.arguments.isNotEmpty()) {
      add(" ", indent, false)
      add(element.arguments, indent)
    }
    else {
      add("(", indent, false)
      add(element.arguments, indent)
      add(")", indent, false)
    }
  }

  private fun addCodeElement(element: CodeElement, indent: Int, isNewLine: Boolean) {
    for (line in element.text) {
      add(line, indent, isNewLine)
    }
  }

  private fun addInfixCallElement(element: InfixCall, indent: Int, isNewLine: Boolean) {
    add(element.left, indent, isNewLine)
    add(" ", indent, false)
    add(element.name, indent, false)
    add(" ", indent, false)
    add(element.right, indent, false)
  }

  private fun addIntElement(element: IntElement, indent: Int, isNewLine: Boolean) {
    add(element.value.toString(), indent, isNewLine)
  }

  private fun addBooleanElement(element: BooleanElement, indent: Int, isNewLine: Boolean) {
    add(element.value.toString(), indent, isNewLine)
  }

  private fun addStringElement(element: StringElement, indent: Int, isNewLine: Boolean) {
    val isUseDoubleQuotes = when (gradleDsl) {
      GradleDsl.GROOVY -> '$' in element.value
      GradleDsl.KOTLIN -> true
    }
    val escapedString = element.value
      .replace("\\", "\\\\")
      .replace("\n", "\\n")
    val string = when (isUseDoubleQuotes) {
      true -> "\"" + escapedString.replace("\"", "\\\"") + "\""
      else -> "\'" + escapedString.replace("\'", "\\\'") + "\'"
    }
    add(string, indent, isNewLine)
  }

  private fun addListElement(element: ListElement, indent: Int, isNewLine: Boolean) {
    when (gradleDsl) {
      GradleDsl.GROOVY -> {
        add("[", indent, isNewLine)
        add(element.elements, indent)
        add("]", indent, false)
      }
      GradleDsl.KOTLIN -> {
        add("listOf(", indent, isNewLine)
        add(element.elements, indent)
        add(")", indent, false)
      }
    }
  }

  private fun addNewLineElement(indent: Int, isNewLine: Boolean) {
    add("", indent, isNewLine)
  }

  private fun add(element: GradleScriptElement, indent: Int, isNewLine: Boolean) {
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

  private fun add(elements: List<GradleScriptElement>, indent: Int) {
    for ((i, argument) in elements.withIndex()) {
      if (i != 0) {
        add(", ", indent, false)
      }
      add(argument, indent, false)
    }
  }

  private fun add(code: String, indent: Int, isNewLine: Boolean) {
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

  private fun hasTrailingBlock(arguments: List<ArgumentElement>): Boolean {
    val last = arguments.lastOrNull() ?: return false
    return last.value is BlockElement && last.name == null
  }
}