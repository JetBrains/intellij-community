// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.importing

@Suppress("MemberVisibilityCanBePrivate", "unused")
class GroovyBuilder {

  private val code = ArrayList<(Int) -> String>()

  fun join(builder: GroovyBuilder) = apply {
    code.addAll(builder.code)
  }

  fun line(line: String) = apply {
    code.add { "    ".repeat(it) + line }
  }

  fun call(name: String, vararg arguments: Any?, configure: (GroovyBuilder.() -> Unit)? = null) = apply {
    when {
      arguments.size == 1 && configure == null -> line("$name ${arguments.first()}")
      configure == null -> line("$name(${arguments.joinToString()})")
      arguments.isEmpty() -> block(name, configure)
      else -> block("$name(${arguments.joinToString()})", configure)
    }
  }

  fun blockIfNotEmpty(name: String, configure: GroovyBuilder.() -> Unit) = blockIfNotEmpty(name, builder(configure))
  fun blockIfNotEmpty(name: String, builder: GroovyBuilder) = apply {
    if (builder.code.isNotEmpty()) {
      block(name, builder)
    }
  }

  fun block(name: String, configure: GroovyBuilder.() -> Unit) = block(name, builder(configure))
  fun block(name: String, builder: GroovyBuilder) = apply {
    line("$name {")
    code.add { builder.generate(it + 1) }
    line("}")
  }

  fun property(name: String, value: Any?) = line("$name = $value")
  fun propertyIfNotNull(name: String, value: Any?) = apply {
    if (value != null) {
      property(name, value)
    }
  }

  fun generate(indent: Int = 0) = code.joinToString("\n") { it(indent) }

  companion object {
    private fun builder(configure: GroovyBuilder.() -> Unit) = GroovyBuilder().apply(configure)

    fun groovy(configure: GroovyBuilder.() -> Unit) = builder(configure).generate()
  }
}