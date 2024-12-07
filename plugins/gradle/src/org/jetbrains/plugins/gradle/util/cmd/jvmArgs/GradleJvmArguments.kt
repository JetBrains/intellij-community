// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.cmd.jvmArgs

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.util.cmd.GradleCommandLineTokenizer
import org.jetbrains.plugins.gradle.util.cmd.jvmArgs.GradleJvmArgument.OptionNotation
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineNode
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineOption.PropertyNotation
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineOption.ShortNotation

@ApiStatus.Experimental
class GradleJvmArguments(val arguments: List<GradleJvmArgument>) : GradleCommandLineNode {

  override val tokens: List<String> = arguments.flatMap { it.tokens }

  override val text: String = tokens.joinToString(" ")

  operator fun plus(other: GradleJvmArguments): GradleJvmArguments {
    val otherTexts = other.arguments.map { it.text }
    val otherPropertyNames = other.arguments.asSequence()
      .filterIsInstance<OptionNotation>().map { it.option }
      .filterIsInstance<PropertyNotation>().map { it.propertyName }
      .toSet()
    val otherShortNames = other.arguments.asSequence()
      .filterIsInstance<OptionNotation>().map { it.option }
      .filterIsInstance<ShortNotation>().map { it.name }
      .toSet()
      .intersect(setOf("-Xmx", "-Xms"))

    val nonOverridenArguments = arguments
      .filterNot { it.text in otherTexts }
      .filterNot { it is OptionNotation && it.option is PropertyNotation && it.option.propertyName in otherPropertyNames }
      .filterNot { it is OptionNotation && it.option is ShortNotation && it.option.name in otherShortNames }

    return GradleJvmArguments(nonOverridenArguments + other.arguments)
  }

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