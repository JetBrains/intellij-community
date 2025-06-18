// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.ArgumentElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.AssignElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression.BlockElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression.BooleanElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression.CallElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression.CodeElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression.InfixCall
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression.IntElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression.ListElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.Expression.StringElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.NewLineElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.PlusAssignElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.GradleScriptElement.Statement.PropertyElement

class GradleScriptTreeBuilder : AbstractGradleScriptElementBuilder() {

  private val roots = ArrayList<GradleScriptElement>()

  fun join(builder: GradleScriptTreeBuilder): BlockElement {
    val block = builder.generate()
    addElements(block)
    return block
  }

  fun addElement(statement: Statement): GradleScriptTreeBuilder = apply {
    roots.add(statement)
  }

  fun addElements(block: BlockElement): GradleScriptTreeBuilder = addElements(block) { true }

  fun addNonExistedElements(block: BlockElement): GradleScriptTreeBuilder = addElements(block) { it !in roots }

  private fun addElements(block: BlockElement, filter: (GradleScriptElement) -> Boolean): GradleScriptTreeBuilder = apply {
    for (statement in block.statements) {
      if (filter(statement)) {
        addElement(statement)
      }
    }
  }

  private fun <E : GradleScriptElement> process(vararg children: GradleScriptElement, element: () -> E): E = process(children.toList(), element)
  private fun <E : GradleScriptElement> process(children: List<GradleScriptElement>, createElement: () -> E): E {
    for (child in children) {
      roots.removeIf { it === child }
    }
    val element = createElement()
    roots.add(element)
    return element
  }

  override fun newLine(): NewLineElement = process { super.newLine() }
  override fun int(value: Int): IntElement = process { super.int(value) }
  override fun boolean(value: Boolean): BooleanElement = process { super.boolean(value) }
  override fun string(value: String): StringElement = process { super.string(value) }
  override fun list(elements: List<Expression>): ListElement = process(elements) { super.list(elements) }
  override fun code(text: List<String>): CodeElement = process { super.code(text) }
  override fun assign(left: Expression, right: Expression): AssignElement = process(left, right) { super.assign(left, right) }
  override fun plusAssign(name: String, value: Expression): PlusAssignElement = process(value) { super.plusAssign(name, value) }
  override fun property(name: String, value: Expression): PropertyElement = process(value) { super.property(name, value) }
  override fun call(name: Expression, arguments: List<ArgumentElement>): CallElement = process(arguments + name) { super.call(name, arguments) }
  override fun infixCall(left: Expression, name: String, right: Expression): InfixCall = process(left, right) { super.infixCall(left, name, right) }
  override fun argument(name: String?, value: Expression): ArgumentElement = process(value) { super.argument(name, value) }
  override fun block(configure: GradleScriptTreeBuilder.() -> Unit): BlockElement = process { super.block(configure) }

  fun generate(): BlockElement {
    val statements = roots.filterIsInstance<Statement>()
    if (statements.size != roots.size) {
      LOG.error("Found non complete script tree. Orphan elements: " +
                roots.filterNot { it is Statement })
    }
    return BlockElement(statements.dropLastWhile { it == NewLineElement })
  }

  companion object {
    private val LOG = Logger.getInstance(GradleScriptTreeBuilder::class.java)

    fun tree(configure: GradleScriptTreeBuilder.() -> Unit): BlockElement =
      GradleScriptTreeBuilder().apply(configure).generate()
  }
}