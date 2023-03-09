// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script


sealed class ScriptElement {
  data class ArgumentElement(val name: String?, val value: Statement.Expression) : ScriptElement()
  sealed class Statement : ScriptElement() {
    object NewLineElement : Statement()
    data class PropertyElement(val name: String, val value: Expression) : Statement()
    data class AssignElement(val left: Expression, val right: Expression) : Statement()
    data class PlusAssignElement(val name: String, val value: Expression) : Statement()
    sealed class Expression : Statement() {
      data class IntElement(val value: Int) : Expression()
      data class BooleanElement(val value: Boolean) : Expression()
      data class StringElement(val value: String) : Expression()
      data class ListElement(val elements: List<Expression>) : Expression()
      data class CodeElement(val text: List<String>) : Expression()
      data class InfixCall(val left: Expression, val name: String, val right: Expression) : Expression()
      data class CallElement(val name: Expression, val arguments: List<ArgumentElement>) : Expression()
      data class BlockElement(val statements: List<Statement>) : Expression() {
        fun isEmpty(): Boolean = statements.all { it is BlockElement && it.isEmpty() }
      }
    }
  }
}
