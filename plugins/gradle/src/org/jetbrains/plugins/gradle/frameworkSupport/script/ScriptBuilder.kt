// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.frameworkSupport.script

import java.util.function.Consumer

interface ScriptBuilder<SB : ScriptBuilder<SB>> {

  operator fun contains(builder: SB): Boolean

  fun join(builder: SB): SB

  fun code(vararg text: String): SB

  fun call(name: String): SB

  fun call(
    name: String,
    firstArgument: String,
    vararg arguments: String,
    configure: (SB.() -> Unit)? = null
  ): SB

  fun call(
    name: String,
    firstArgument: Pair<String, String>,
    vararg arguments: Pair<String, String>,
    configure: (SB.() -> Unit)? = null
  ): SB

  fun blockIfNotEmpty(name: String, configure: Consumer<SB>): SB
  fun blockIfNotEmpty(name: String, configure: SB.() -> Unit): SB
  fun blockIfNotEmpty(name: String, builder: SB): SB

  fun block(name: String, configure: Consumer<SB>): SB
  fun block(name: String, configure: SB.() -> Unit): SB
  fun block(name: String, builder: SB): SB

  fun assign(name: String, value: String): SB
  fun assignIfNotNull(name: String, value: String?): SB

  fun generate(indent: Int = 0): String

  /**
   * Surrounds script string by quotes
   */
  fun str(string: String): String
}