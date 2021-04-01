// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import kotlin.apply as applyKt

class GroovyScriptBuilder : AbstractScriptBuilder<GroovyScriptBuilder>() {

  override fun apply(action: GroovyScriptBuilder.() -> Unit) = applyKt(action)

  override fun call(name: String) = apply {
    code("$name()")
  }

  override fun call(
    name: String,
    firstArgument: String,
    vararg arguments: String,
    configure: (GroovyScriptBuilder.() -> Unit)?
  ) = call(name, listOf(firstArgument, *arguments), configure)

  override fun call(
    name: String,
    firstArgument: Pair<String, String>,
    vararg arguments: Pair<String, String>,
    configure: (GroovyScriptBuilder.() -> Unit)?
  ) = call(name, listOf(firstArgument, *arguments).map { (arg, value) -> "$arg: $value" }, configure)

  private fun call(name: String, arguments: Iterable<Any>, configure: (GroovyScriptBuilder.() -> Unit)? = null) = apply {
    when (configure) {
      null -> code("$name ${arguments.joinToString()}")
      else -> block("$name(${arguments.joinToString()})", configure)
    }
  }

  override fun str(string: String) = when ('$' in string) {
    true -> """"$string""""
    else -> "'$string'"
  }

  companion object {
    fun groovy(configure: GroovyScriptBuilder.() -> Unit) = GroovyScriptBuilder().apply(configure).generate()
  }
}