// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.cmd.jvmArgs

import org.apache.commons.cli.Option
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.util.cmd.GradleCommandLineOptionParser
import org.jetbrains.plugins.gradle.util.cmd.GradleCommandLineTokenizer
import org.jetbrains.plugins.gradle.util.cmd.jvmArgs.GradleJvmArgument.OptionNotation
import org.jetbrains.plugins.gradle.util.cmd.jvmArgs.GradleJvmArgument.ValueNotation

@ApiStatus.Internal
class GradleJvmArgumentsParser(private val tokenizer: GradleCommandLineTokenizer) {

  private val optionParser = SyntheticOptionParser(ALL_OPTIONS, tokenizer)

  fun parse(): GradleJvmArguments {
    val arguments = ArrayList<GradleJvmArgument>()
    while (!tokenizer.isEof()) {
      val argument = parseArgument()
      arguments.add(argument)
    }
    return GradleJvmArguments(arguments)
  }

  private fun parseArgument(): GradleJvmArgument {
    val marker = tokenizer.mark()
    val option = optionParser.tryParseOption()
    if (option != null) {
      return OptionNotation(option)
    }
    tokenizer.rollback(marker)
    val value = tokenizer.expected()
    return ValueNotation(value)
  }

  private class SyntheticOptionParser(
    options: Collection<Option>,
    tokenizer: GradleCommandLineTokenizer,
  ) : GradleCommandLineOptionParser(options, tokenizer) {

    override fun getOption(token: String): Option? {
      return super.getOption(token) ?: getSyntheticOption(token)
    }

    private fun getSyntheticOption(token: String): Option? {
      try {
        return when {
          token.startsWith("--") -> {
            val longName = token.substring(2)
            Option.builder().longOpt(longName).hasArg().build()
          }
          token.startsWith("-") -> {
            val shortName = token.substring(1)
            Option.builder(shortName).hasArg().build()
          }
          else -> null
        }
      }
      catch (_: IllegalArgumentException) {
        // Illegal option name.
        // See org.apache.commons.cli.OptionValidator#validate for details
        return null
      }
    }
  }

  companion object {
    private val ALL_OPTIONS = listOf(
      Option.builder("D").hasArg().build(),
      Option.builder("Xms").hasArg().build(),
      Option.builder("Xmx").hasArg().build(),
      Option.builder().longOpt("add-opens").hasArg().build(),
      Option.builder().longOpt("add-exports").hasArg().build(),
    )
  }
}