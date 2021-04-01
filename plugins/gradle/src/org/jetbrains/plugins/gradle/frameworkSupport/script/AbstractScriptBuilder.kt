// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import java.util.*
import java.util.function.Consumer
import kotlin.collections.ArrayList

abstract class AbstractScriptBuilder<SB : AbstractScriptBuilder<SB>> : ScriptBuilder<SB> {

  private val root = Element.Code(ArrayList())

  protected abstract fun apply(action: SB.() -> Unit): SB

  override fun contains(builder: SB) = builder.root in root.elements

  override fun join(builder: SB) = apply {
    root.elements.add(builder.root)
  }

  override fun code(vararg text: String) = apply {
    for (line in text) {
      root.elements.add(Element.Line(line))
    }
  }

  override fun blockIfNotEmpty(name: String, configure: Consumer<SB>) = blockIfNotEmpty(name, configure::accept)
  override fun blockIfNotEmpty(name: String, configure: SB.() -> Unit) = blockIfNotEmpty(name, code(configure))
  override fun blockIfNotEmpty(name: String, builder: SB) = blockIfNotEmpty(name, builder.root)
  private fun blockIfNotEmpty(name: String, code: Element.Code) = apply {
    if (!code.isEmpty()) {
      block(name, code)
    }
  }

  override fun block(name: String, configure: Consumer<SB>) = block(name, configure::accept)
  override fun block(name: String, configure: SB.() -> Unit) = block(name, code(configure))
  override fun block(name: String, builder: SB) = block(name, builder.root)
  private fun block(name: String, code: Element.Code) = apply {
    root.elements.add(Element.Block(name, code))
  }

  private fun code(configure: SB.() -> Unit): Element.Code {
    val size = root.elements.size
    apply(configure)
    val elementsView = root.elements.subList(size, root.elements.size)
    val elements = elementsView.toMutableList()
    elementsView.clear()
    return Element.Code(elements)
  }

  override fun assign(name: String, value: String) = code("$name = $value")
  override fun assignIfNotNull(name: String, value: String?) = apply {
    if (value != null) {
      assign(name, value)
    }
  }

  override fun generate(indent: Int) = root.generate(indent)

  private sealed class Element {
    abstract fun generate(indent: Int): String

    override fun equals(other: Any?): Boolean {
      return other is Element && generate(0) == other.generate(0)
    }

    override fun hashCode() = generate(0).hashCode()

    class Line(val line: String) : Element() {
      override fun generate(indent: Int) = INDENT.repeat(indent) + line
    }

    class Block(val name: String, val code: Code) : Element() {
      override fun generate(indent: Int) = StringJoiner("\n").apply {
        if (code.isEmpty()) {
          add(INDENT.repeat(indent) + "$name {}")
        }
        else {
          add(INDENT.repeat(indent) + "$name {")
          add(code.generate(indent + 1))
          add(INDENT.repeat(indent) + "}")
        }
      }.toString()
    }

    class Code(val elements: MutableList<Element>) : Element() {
      fun isEmpty(): Boolean = elements.all { it is Code && it.isEmpty() }

      override fun generate(indent: Int) = elements
        .filter { it !is Code || !it.isEmpty() }
        .joinToString("\n") { it.generate(indent) }
    }
  }

  companion object {
    private const val INDENT = "    "
  }
}