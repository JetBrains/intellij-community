// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.ArgumentElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression.BlockElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.NewLineElement

@Suppress("MemberVisibilityCanBePrivate", "unused")
class ScriptTreeBuilder : AbstractScriptElementBuilder() {

  private val roots = ArrayList<ScriptElement>()

  fun join(builder: ScriptTreeBuilder) = builder.generate().also(::addElements)

  fun addElement(statement: Statement) = apply {
    roots.add(statement)
  }

  fun addElements(block: BlockElement) = addElements(block) { true }
  fun addElements(builder: ScriptTreeBuilder) = addElements(builder.generate())
  fun addElements(configure: ScriptTreeBuilder.() -> Unit) = addElements(ScriptTreeBuilder(configure))

  fun addNonExistedElements(block: BlockElement) = addElements(block) { it !in roots }
  fun addNonExistedElements(builder: ScriptTreeBuilder) = addNonExistedElements(builder.generate())
  fun addNonExistedElements(configure: ScriptTreeBuilder.() -> Unit) = addNonExistedElements(ScriptTreeBuilder(configure))

  fun addElements(block: BlockElement, filter: (ScriptElement) -> Boolean) = apply {
    for (statement in block.statements) {
      if (filter(statement)) {
        addElement(statement)
      }
    }
  }

  private fun <E : ScriptElement> process(vararg children: ScriptElement, element: () -> E) = process(children.toList(), element)
  private fun <E : ScriptElement> process(children: List<ScriptElement>, createElement: () -> E): E {
    for (child in children) {
      roots.removeIf { it === child }
    }
    val element = createElement()
    roots.add(element)
    return element
  }

  override fun newLine() = process { super.newLine() }
  override fun int(value: Int) = process { super.int(value) }
  override fun boolean(value: Boolean) = process { super.boolean(value) }
  override fun string(value: String) = process { super.string(value) }
  override fun list(elements: List<Expression>) = process(elements) { super.list(elements) }
  override fun code(text: List<String>) = process { super.code(text) }
  override fun assign(left: Expression, right: Expression) = process(left, right) { super.assign(left, right) }
  override fun plusAssign(name: String, value: Expression) = process(value) { super.plusAssign(name, value) }
  override fun property(name: String, value: Expression) = process(value) { super.property(name, value) }
  override fun call(name: Expression, arguments: List<ArgumentElement>) = process(arguments + name) { super.call(name, arguments) }
  override fun infixCall(left: Expression, name: String, right: Expression) = process(left, right) { super.infixCall(left, name, right) }
  override fun argument(name: String?, value: Expression) = process(value) { super.argument(name, value) }
  override fun block(configure: ScriptTreeBuilder.() -> Unit) = process { super.block(configure) }

  fun generate(): BlockElement {
    val statements = roots.filterIsInstance<Statement>()
    if (statements.size != roots.size) {
      LOG.error("Found non complete script tree. Orphan elements: " +
                roots.filterNot { it is Statement })
    }
    return BlockElement(statements.dropLastWhile { it == NewLineElement })
  }

  companion object {
    private val LOG = Logger.getInstance(ScriptTreeBuilder::class.java)

    operator fun invoke(configure: ScriptTreeBuilder.() -> Unit) =
      ScriptTreeBuilder().apply(configure)

    fun tree(configure: ScriptTreeBuilder.() -> Unit) =
      ScriptTreeBuilder(configure).generate()

    fun script(builder: ScriptBuilder, configure: ScriptTreeBuilder.() -> Unit) =
      builder.generate(tree(configure))

    fun script(useKotlinDsl: Boolean = false, configure: ScriptTreeBuilder.() -> Unit) =
      when (useKotlinDsl) {
        true -> script(KotlinScriptBuilder(), configure)
        else -> script(GroovyScriptBuilder(), configure)
      }
  }
}