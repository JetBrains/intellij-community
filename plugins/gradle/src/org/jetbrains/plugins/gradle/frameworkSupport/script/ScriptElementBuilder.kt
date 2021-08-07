// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.ArgumentElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.*
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression.*
import java.util.function.Consumer

@Suppress("unused")
interface ScriptElementBuilder {

  fun newLine(): NewLineElement
  fun ScriptElement?.ln(): NewLineElement?

  fun string(value: String): StringElement

  fun list(elements: List<Expression>) : ListElement
  fun list(vararg elements: Expression) : ListElement
  fun list(vararg elements: String) : ListElement

  fun code(text: List<String>): CodeElement
  fun code(vararg text: String): CodeElement

  fun assign(name: String, value: Expression): AssignElement
  fun assign(name: String, value: String): AssignElement

  fun assignIfNotNull(name: String, expression: Expression?): AssignElement?
  fun assignIfNotNull(name: String, value: String?): AssignElement?

  fun plusAssign(name: String, value: Expression): PlusAssignElement
  fun plusAssign(name: String, value: String): PlusAssignElement

  fun call(name: Expression, arguments: List<ArgumentElement>): CallElement
  fun call(name: String, arguments: List<ArgumentElement>): CallElement
  fun call(name: String, arguments: List<ArgumentElement>, configure: ScriptTreeBuilder.() -> Unit): CallElement

  fun call(name: String): CallElement
  fun call(name: String, configure: Consumer<ScriptTreeBuilder>): CallElement
  fun call(name: String, configure: ScriptTreeBuilder.() -> Unit): CallElement

  fun call(name: String, vararg arguments: ArgumentElement): CallElement
  fun call(name: String, vararg arguments: ArgumentElement, configure: ScriptTreeBuilder.() -> Unit): CallElement

  fun call(name: String, vararg arguments: Expression): CallElement
  fun call(name: String, vararg arguments: Expression, configure: ScriptTreeBuilder.() -> Unit): CallElement

  fun call(name: String, vararg arguments: String): CallElement
  fun call(name: String, vararg arguments: String, configure: ScriptTreeBuilder.() -> Unit): CallElement

  fun call(name: String, vararg arguments: Pair<String, String>): CallElement
  fun call(name: String, vararg arguments: Pair<String, String>, configure: ScriptTreeBuilder.() -> Unit): CallElement

  fun callIfNotEmpty(name: String, block: BlockElement): CallElement?
  fun callIfNotEmpty(name: String, builder: ScriptTreeBuilder): CallElement?
  fun callIfNotEmpty(name: String, configure: ScriptTreeBuilder.() -> Unit): CallElement?

  fun infixCall(left: Expression, name: String, right: Expression): InfixCall

  fun argument(name: String?, value: Expression): ArgumentElement
  fun argument(name: String?, value: String): ArgumentElement
  fun argument(argument: Pair<String, String>): ArgumentElement
  fun argument(value: Expression): ArgumentElement
  fun argument(value: String): ArgumentElement
  fun argument(configure: ScriptTreeBuilder.() -> Unit): ArgumentElement

  fun block(configure: ScriptTreeBuilder.() -> Unit): BlockElement
}