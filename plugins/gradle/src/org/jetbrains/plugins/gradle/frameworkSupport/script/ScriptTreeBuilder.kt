// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.ArgumentElement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression
import org.jetbrains.plugins.gradle.frameworkSupport.script.ScriptElement.Statement.Expression.BlockElement

@Suppress("MemberVisibilityCanBePrivate", "unused")
class ScriptTreeBuilder : AbstractScriptElementBuilder() {

  private val roots = ArrayList<ScriptElement>()

  operator fun contains(builder: ScriptTreeBuilder) = roots.containsAll(builder.generate().statements)

  fun join(builder: ScriptTreeBuilder) = roots.addAll(builder.generate().statements)

  private fun <E : ScriptElement> process(vararg children: ScriptElement, element: () -> E) = process(children.toList(), element)
  private fun <E : ScriptElement> process(children: List<ScriptElement>, createElement: () -> E): E {
    for (child in children) {
      roots.removeIf { it === child }
    }
    val element = createElement()
    roots.add(element)
    return element
  }

  override fun string(value: String) = process { super.string(value) }
  override fun code(text: List<String>) = process { super.code(text) }
  override fun assign(name: String, value: Expression) = process(value) { super.assign(name, value) }
  override fun plusAssign(name: String, value: Expression) = process(value) { super.plusAssign(name, value) }
  override fun call(name: String, arguments: List<ArgumentElement>) = process(arguments) { super.call(name, arguments) }
  override fun infixCall(left: Expression, name: String, right: Expression) = process(left, right) { super.infixCall(left, name, right) }
  override fun argument(name: String?, value: Expression) = process(value) { super.argument(name, value) }
  override fun block(configure: ScriptTreeBuilder.() -> Unit) = process { super.block(configure) }

  fun generate(): BlockElement {
    val statements = roots.filterIsInstance<Statement>()
    if (statements.size != roots.size) {
      LOG.error("Found non complete script tree. Orphan elements: " +
                roots.filterNot { it is Statement })
    }
    return BlockElement(statements)
  }

  companion object {
    private val LOG = Logger.getInstance(ScriptTreeBuilder::class.java)

    operator fun invoke(configure: ScriptTreeBuilder.() -> Unit) =
      ScriptTreeBuilder().apply(configure)

    fun tree(configure: ScriptTreeBuilder.() -> Unit) =
      ScriptTreeBuilder(configure).generate()

    fun script(builder: ScriptBuilder, configure: ScriptTreeBuilder.() -> Unit) =
      builder.generate(tree(configure))
  }
}