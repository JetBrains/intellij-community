// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.cmd.jvmArgs

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.util.cmd.GradleCommandLineTokenizer
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineNode

@ApiStatus.Experimental
class GradleJvmArguments(val arguments: List<GradleJvmArgument>) : GradleCommandLineNode {

  override val tokens: List<String> = arguments.flatMap { it.tokens }

  override val text: String = tokens.joinToString(" ")

  companion object {

    @JvmField
    val EMPTY = GradleJvmArguments(emptyList())

    @JvmStatic
    fun parse(jvmArguments: String): GradleJvmArguments {
      return GradleJvmArgumentsParser(GradleCommandLineTokenizer(jvmArguments)).parse()
    }

    @JvmStatic
    fun parse(jvmArguments: List<String>): GradleJvmArguments {
      return GradleJvmArgumentsParser(GradleCommandLineTokenizer(jvmArguments)).parse()
    }
  }
}