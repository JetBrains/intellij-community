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

  override fun newLine() = NewLineElement
  override fun ScriptElement?.ln() = if (this == null || this is BlockElement && isEmpty()) null else newLine()

  override fun int(value: Int) = IntElement(value)
  override fun boolean(value: Boolean) = BooleanElement(value)
  override fun string(value: String) = StringElement(value)

  override fun list(elements: List<Expression>) = ListElement(elements)
  override fun list(vararg elements: Expression) = list(elements.toList())
  override fun list(vararg elements: String) = list(elements.map(::string))

  override fun code(text: List<String>) = CodeElement(text)
  override fun code(vararg text: String) = code(text.toList())

  override fun assign(left: Expression, right: Expression) = AssignElement(left, right)
  override fun assign(left: Expression, right: String) = assign(left, string(right))
  override fun assign(left: Expression, right: Int) = assign(left, int(right))
  override fun assign(left: Expression, right: Boolean) = assign(left, boolean(right))

  override fun assign(name: String, value: Expression) = assign(code(name), value)
  override fun assign(name: String, value: String) = assign(name, string(value))
  override fun assign(name: String, value: Int) = assign(name, int(value))
  override fun assign(name: String, value: Boolean) = assign(name, boolean(value))

  override fun assignIfNotNull(name: String, expression: Expression?) = expression?.let { assign(name, it) }
  override fun assignIfNotNull(name: String, value: String?) = value?.let { assign(name, it) }

  override fun plusAssign(name: String, value: Expression) = PlusAssignElement(name, value)
  override fun plusAssign(name: String, value: String) = plusAssign(name, string(value))

  override fun property(name: String, value: Expression) = PropertyElement(name, value)
  override fun property(name: String, value: String) = property(name, string(value))
  override fun property(name: String, value: Int) = property(name, int(value))
  override fun property(name: String, value: Boolean) = property(name, boolean(value))

  override fun call(name: Expression, arguments: List<ArgumentElement>) = CallElement(name, arguments)
  override fun call(name: String, arguments: List<ArgumentElement>) = call(code(name), arguments)
  override fun call(name: String, arguments: List<ArgumentElement>, configure: ScriptTreeBuilder.() -> Unit) =
    call(name, arguments + argument(configure))

  override fun call(name: String) = call(name, emptyList())
  override fun call(name: String, configure: Consumer<ScriptTreeBuilder>) = call(name, configure::accept)
  override fun call(name: String, configure: ScriptTreeBuilder.() -> Unit) =
    call(name, emptyList(), configure)

  override fun call(name: String, vararg arguments: ArgumentElement) = call(name, arguments.toList())
  override fun call(name: String, vararg arguments: ArgumentElement, configure: ScriptTreeBuilder.() -> Unit) =
    call(name, arguments.toList(), configure)

  override fun call(name: String, vararg arguments: Expression) = call(name, arguments.map(::argument))
  override fun call(name: String, vararg arguments: Expression, configure: ScriptTreeBuilder.() -> Unit) =
    call(name, arguments.map(::argument), configure)

  override fun call(name: String, vararg arguments: String) = call(name, arguments.map(::argument))
  override fun call(name: String, vararg arguments: String, configure: ScriptTreeBuilder.() -> Unit) =
    call(name, arguments.map(::argument), configure)

  override fun call(name: String, vararg arguments: Pair<String, String>) = call(name, arguments.map(::argument))
  override fun call(name: String, vararg arguments: Pair<String, String>, configure: ScriptTreeBuilder.() -> Unit) =
    call(name, arguments.map(::argument), configure)

  override fun callIfNotEmpty(name: String, block: BlockElement) = if (block.isEmpty()) null else call(name, block)
  override fun callIfNotEmpty(name: String, builder: ScriptTreeBuilder) = callIfNotEmpty(name, builder.generate())
  override fun callIfNotEmpty(name: String, configure: ScriptTreeBuilder.() -> Unit) = callIfNotEmpty(name, tree(configure))

  override fun infixCall(left: Expression, name: String, right: Expression) = InfixCall(left, name, right)

  override fun argument(name: String?, value: Expression) = ArgumentElement(name, value)
  override fun argument(name: String?, value: String) = argument(name, string(value))
  override fun argument(argument: Pair<String, String>) = argument(argument.first, argument.second)
  override fun argument(value: Expression) = argument(null, value)
  override fun argument(value: String) = argument(null, value)
  override fun argument(configure: ScriptTreeBuilder.() -> Unit) = argument(block(configure))

  override fun block(configure: ScriptTreeBuilder.() -> Unit) = tree(configure)
}