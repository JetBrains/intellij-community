// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.ArgumentElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.*
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression.*
import java.util.function.Consumer

@ApiStatus.NonExtendable
interface GradleScriptElementBuilder {

  fun newLine(): NewLineElement
  fun GradleScriptElement?.ln(): NewLineElement?

  fun int(value: Int): IntElement
  fun boolean(value: Boolean): BooleanElement
  fun string(value: String): StringElement

  fun list(elements: List<Expression>) : ListElement
  fun list(vararg elements: Expression) : ListElement
  fun list(vararg elements: String) : ListElement

  fun code(text: List<String>): CodeElement
  fun code(vararg text: String): CodeElement

  fun assign(left: Expression, right: Expression): AssignElement
  fun assign(left: Expression, right: String): AssignElement
  fun assign(left: Expression, right: Int): AssignElement
  fun assign(left: Expression, right: Boolean): AssignElement

  fun assign(name: String, value: Expression): AssignElement
  fun assign(name: String, value: String): AssignElement
  fun assign(name: String, value: Int): AssignElement
  fun assign(name: String, value: Boolean): AssignElement

  fun assignIfNotNull(name: String, expression: Expression?): AssignElement?
  fun assignIfNotNull(name: String, value: String?): AssignElement?
  fun assignIfNotNull(name: String, value: Boolean?): AssignElement?

  fun plusAssign(name: String, value: Expression): PlusAssignElement
  fun plusAssign(name: String, value: String): PlusAssignElement

  fun property(name: String, value: Expression): PropertyElement
  fun property(name: String, value: String): PropertyElement
  fun property(name: String, value: Int): PropertyElement
  fun property(name: String, value: Boolean): PropertyElement

  fun call(name: Expression, arguments: List<ArgumentElement>): CallElement
  fun call(name: String, arguments: List<ArgumentElement>): CallElement
  fun call(name: String, arguments: List<ArgumentElement>, configure: GradleScriptTreeBuilder.() -> Unit): CallElement

  fun call(name: String): CallElement
  fun call(name: String, configure: Consumer<GradleScriptTreeBuilder>): CallElement
  fun call(name: String, configure: GradleScriptTreeBuilder.() -> Unit): CallElement

  fun call(name: String, vararg arguments: ArgumentElement): CallElement
  fun call(name: String, vararg arguments: ArgumentElement, configure: GradleScriptTreeBuilder.() -> Unit): CallElement

  fun call(name: String, vararg arguments: Expression): CallElement
  fun call(name: String, vararg arguments: Expression, configure: GradleScriptTreeBuilder.() -> Unit): CallElement

  fun call(name: String, vararg arguments: String): CallElement
  fun call(name: String, vararg arguments: String, configure: GradleScriptTreeBuilder.() -> Unit): CallElement

  fun call(name: String, vararg arguments: Pair<String, String>): CallElement
  fun call(name: String, vararg arguments: Pair<String, String>, configure: GradleScriptTreeBuilder.() -> Unit): CallElement

  fun callIfNotEmpty(name: String, block: BlockElement): CallElement?
  fun callIfNotEmpty(name: String, builder: GradleScriptTreeBuilder): CallElement?
  fun callIfNotEmpty(name: String, configure: GradleScriptTreeBuilder.() -> Unit): CallElement?

  fun infixCall(left: Expression, name: String, right: Expression): InfixCall

  fun argument(name: String?, value: Expression): ArgumentElement
  fun argument(name: String?, value: String): ArgumentElement
  fun argument(argument: Pair<String, String>): ArgumentElement
  fun argument(value: Expression): ArgumentElement
  fun argument(value: String): ArgumentElement
  fun argument(configure: GradleScriptTreeBuilder.() -> Unit): ArgumentElement

  fun block(configure: GradleScriptTreeBuilder.() -> Unit): BlockElement
}