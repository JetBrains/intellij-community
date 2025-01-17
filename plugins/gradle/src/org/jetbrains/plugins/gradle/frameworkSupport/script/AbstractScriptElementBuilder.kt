// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.ArgumentElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.*
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression.*
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptTreeBuilder.Companion.tree
import java.util.function.Consumer

@ApiStatus.NonExtendable
abstract class AbstractScriptElementBuilder : ScriptElementBuilder {

  override fun newLine(): NewLineElement = NewLineElement
  override fun ScriptElement?.ln(): NewLineElement? = if (this == null || this is BlockElement && isEmpty()) null else newLine()

  override fun int(value: Int): IntElement = IntElement(value)
  override fun boolean(value: Boolean): BooleanElement = BooleanElement(value)
  override fun string(value: String): StringElement = StringElement(value)

  override fun list(elements: List<Expression>): ListElement = ListElement(elements)
  override fun list(vararg elements: Expression): ListElement = list(elements.toList())
  override fun list(vararg elements: String): ListElement = list(elements.map(::string))

  override fun code(text: List<String>): CodeElement = CodeElement(text)
  override fun code(vararg text: String): CodeElement = code(text.toList())

  override fun assign(left: Expression, right: Expression): AssignElement = AssignElement(left, right)
  override fun assign(left: Expression, right: String): AssignElement = assign(left, string(right))
  override fun assign(left: Expression, right: Int): AssignElement = assign(left, int(right))
  override fun assign(left: Expression, right: Boolean): AssignElement = assign(left, boolean(right))

  override fun assign(name: String, value: Expression): AssignElement = assign(code(name), value)
  override fun assign(name: String, value: String): AssignElement = assign(name, string(value))
  override fun assign(name: String, value: Int): AssignElement = assign(name, int(value))
  override fun assign(name: String, value: Boolean): AssignElement = assign(name, boolean(value))

  override fun assignIfNotNull(name: String, expression: Expression?): AssignElement? = expression?.let { assign(name, it) }
  override fun assignIfNotNull(name: String, value: String?): AssignElement? = value?.let { assign(name, it) }
  override fun assignIfNotNull(name: String, value: Boolean?): AssignElement? = value?.let { assign(name, it) }

  override fun plusAssign(name: String, value: Expression): PlusAssignElement = PlusAssignElement(name, value)
  override fun plusAssign(name: String, value: String): PlusAssignElement = plusAssign(name, string(value))

  override fun property(name: String, value: Expression): PropertyElement = PropertyElement(name, value)
  override fun property(name: String, value: String): PropertyElement = property(name, string(value))
  override fun property(name: String, value: Int): PropertyElement = property(name, int(value))
  override fun property(name: String, value: Boolean): PropertyElement = property(name, boolean(value))

  override fun call(name: Expression, arguments: List<ArgumentElement>): CallElement = CallElement(name, arguments)
  override fun call(name: String, arguments: List<ArgumentElement>): CallElement = call(code(name), arguments)
  override fun call(name: String, arguments: List<ArgumentElement>, configure: ScriptTreeBuilder.() -> Unit): CallElement =
    call(name, arguments + argument(configure))

  override fun call(name: String): CallElement = call(name, emptyList())
  override fun call(name: String, configure: Consumer<ScriptTreeBuilder>): CallElement = call(name, configure::accept)
  override fun call(name: String, configure: ScriptTreeBuilder.() -> Unit): CallElement =
    call(name, emptyList(), configure)

  override fun call(name: String, vararg arguments: ArgumentElement): CallElement = call(name, arguments.toList())
  override fun call(name: String, vararg arguments: ArgumentElement, configure: ScriptTreeBuilder.() -> Unit): CallElement =
    call(name, arguments.toList(), configure)

  override fun call(name: String, vararg arguments: Expression): CallElement = call(name, arguments.map(::argument))
  override fun call(name: String, vararg arguments: Expression, configure: ScriptTreeBuilder.() -> Unit): CallElement =
    call(name, arguments.map(::argument), configure)

  override fun call(name: String, vararg arguments: String): CallElement = call(name, arguments.map(::argument))
  override fun call(name: String, vararg arguments: String, configure: ScriptTreeBuilder.() -> Unit): CallElement =
    call(name, arguments.map(::argument), configure)

  override fun call(name: String, vararg arguments: Pair<String, String>): CallElement = call(name, arguments.map(::argument))
  override fun call(name: String, vararg arguments: Pair<String, String>, configure: ScriptTreeBuilder.() -> Unit): CallElement =
    call(name, arguments.map(::argument), configure)

  override fun callIfNotEmpty(name: String, block: BlockElement): CallElement? = if (block.isEmpty()) null else call(name, block)
  override fun callIfNotEmpty(name: String, builder: ScriptTreeBuilder): CallElement? = callIfNotEmpty(name, builder.generate())
  override fun callIfNotEmpty(name: String, configure: ScriptTreeBuilder.() -> Unit): CallElement? = callIfNotEmpty(name, tree(configure))

  override fun infixCall(left: Expression, name: String, right: Expression): InfixCall = InfixCall(left, name, right)

  override fun argument(name: String?, value: Expression): ArgumentElement = ArgumentElement(name, value)
  override fun argument(name: String?, value: String): ArgumentElement = argument(name, string(value))
  override fun argument(argument: Pair<String, String>): ArgumentElement = argument(argument.first, argument.second)
  override fun argument(value: Expression): ArgumentElement = argument(null, value)
  override fun argument(value: String): ArgumentElement = argument(null, value)
  override fun argument(configure: ScriptTreeBuilder.() -> Unit): ArgumentElement = argument(block(configure))

  override fun block(configure: ScriptTreeBuilder.() -> Unit): BlockElement = tree(configure)
}