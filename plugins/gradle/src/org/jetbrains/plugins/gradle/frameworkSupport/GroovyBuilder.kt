// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport

import java.util.*
import java.util.function.Consumer
import kotlin.collections.ArrayList

@Suppress("MemberVisibilityCanBePrivate", "unused")
class GroovyBuilder() {

  private val root = Element.Code()

  constructor(configure: GroovyBuilder.() -> Unit) : this() {
    this.configure()
  }

  operator fun contains(builder: GroovyBuilder) = builder.root in root.elements

  fun join(builder: GroovyBuilder) = apply {
    root.elements.add(builder.root)
  }

  fun code(vararg text: String) = apply {
    for (line in text) {
      root.elements.add(Element.Line(line))
    }
  }

  fun call(name: String) = apply {
    code("$name()")
  }

  fun call(
    name: String,
    firstArgument: String,
    vararg arguments: String,
    configure: (GroovyBuilder.() -> Unit)? = null
  ) = call(name, listOf(firstArgument, *arguments), configure)

  fun call(
    name: String,
    firstArgument: Pair<String, String>,
    vararg arguments: Pair<String, String>,
    configure: (GroovyBuilder.() -> Unit)? = null
  ) = call(name, listOf(firstArgument, *arguments).map { (arg, value) -> "$arg: $value" }, configure)

  private fun call(name: String, arguments: Iterable<Any>, configure: (GroovyBuilder.() -> Unit)? = null) = apply {
    when (configure) {
      null -> code("$name ${arguments.joinToString()}")
      else -> block("$name(${arguments.joinToString()})", configure)
    }
  }

  fun blockIfNotEmpty(name: String, configure: Consumer<GroovyBuilder>) = blockIfNotEmpty(name, configure::accept)
  fun blockIfNotEmpty(name: String, configure: GroovyBuilder.() -> Unit) = blockIfNotEmpty(name, GroovyBuilder(configure))
  fun blockIfNotEmpty(name: String, builder: GroovyBuilder) = apply {
    if (!builder.root.isEmpty()) {
      block(name, builder)
    }
  }

  fun block(name: String, configure: Consumer<GroovyBuilder>) = block(name, configure::accept)
  fun block(name: String, configure: GroovyBuilder.() -> Unit) = block(name, GroovyBuilder(configure))
  fun block(name: String, builder: GroovyBuilder) = apply {
    root.elements.add(Element.Block(name, builder.root))
  }

  fun assign(name: String, value: String) = code("$name = $value")
  fun assignIfNotNull(name: String, value: String?) = apply {
    if (value != null) {
      assign(name, value)
    }
  }

  fun generate(indent: Int = 0) = root.generate(indent)

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

    class Code : Element() {
      val elements = ArrayList<Element>()

      fun isEmpty(): Boolean = elements.all { it is Code && it.isEmpty() }

      override fun generate(indent: Int) = elements
        .filter { it !is Code || !it.isEmpty() }
        .joinToString("\n") { it.generate(indent) }
    }
  }

  companion object {
    private const val INDENT = "    "

    fun groovy(configure: GroovyBuilder.() -> Unit) = GroovyBuilder(configure).generate()
  }
}