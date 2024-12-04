// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.cmd

import org.apache.commons.cli.Option
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineOption

@ApiStatus.Internal
open class GradleCommandLineOptionParser(
  private val options: Collection<Option>,
  private val tokenizer: GradleCommandLineTokenizer,
) {

  private val optionIndex: Map<String, Option> =
    options.asSequence()
      .filter { it.opt != null }
      .associateBy { "-${it.opt}" } +
    options.asSequence()
      .filter { it.longOpt != null }
      .associateBy { "--${it.longOpt}" }

  protected open fun getOption(token: String): Option? {
    return optionIndex[token]
  }

  fun tryParseOption(): GradleCommandLineOption? {
    val marker = tokenizer.mark()
    val propertyNotation = tryParsePropertyNotation()
    if (propertyNotation != null) return propertyNotation
    tokenizer.rollback(marker)
    val shortOption = tryParseShortNotation()
    if (shortOption != null) return shortOption
    tokenizer.rollback(marker)
    val longOption = tryParseLongNotation()
    if (longOption != null) return longOption
    tokenizer.rollback(marker)
    val option = tryParseVarargNotation()
    if (option != null) return option
    tokenizer.rollback(marker)
    return null
  }

  private fun tryParsePropertyNotation(): GradleCommandLineOption.PropertyNotation? {
    val token = tokenizer.expected()
    for (option in options) {
      val optionName = "-" + (option.opt ?: continue)
      if (option.args == 1 && token != optionName && token.startsWith(optionName)) {
        val value = token.removePrefix(optionName)
        val (propertyName, propertyValue) = parsePropertyOrNull(value) ?: continue
        return GradleCommandLineOption.PropertyNotation(optionName, propertyName, propertyValue)
      }
    }
    return null
  }

  private fun tryParseShortNotation(): GradleCommandLineOption.ShortNotation? {
    val token = tokenizer.expected()
    for (option in options) {
      val optionName = "-" + (option.opt ?: continue)
      if (option.args == 1 && token != optionName && token.startsWith(optionName)) {
        val value = token.removePrefix(optionName)
        return GradleCommandLineOption.ShortNotation(optionName, value)
      }
    }
    return null
  }

  private fun tryParseLongNotation(): GradleCommandLineOption.LongNotation? {
    val token = tokenizer.expected()
    if (!token.startsWith("--")) {
      return null
    }
    val (name, value) = parsePropertyOrNull(token) ?: return null
    val option = getOption(name) ?: return null
    if (option.args != 1 || !token.startsWith("$name=")) {
      return null
    }
    return GradleCommandLineOption.LongNotation(name, value)
  }

  private fun tryParseVarargNotation(): GradleCommandLineOption.VarargNotation? {
    val token = tokenizer.expected()
    val option = getOption(token) ?: return null
    val values = ArrayList<String>()
    when (option.args) {
      Option.UNINITIALIZED -> {}
      Option.UNLIMITED_VALUES -> {
        while (!tokenizer.isEof()) {
          val optionValue = tokenizer.expected()
          values.add(optionValue)
        }
      }
      else -> {
        repeat(option.args) {
          if (!tokenizer.isEof()) {
            val optionValue = tokenizer.expected()
            values.add(optionValue)
          }
        }
      }
    }
    return GradleCommandLineOption.VarargNotation(token, values)
  }

  private fun parsePropertyOrNull(token: String): Pair<String, String>? {
    if ('=' !in token) {
      return null
    }
    val name = token.substringBefore('=')
    val value = token.substringAfter('=')
    if ('"' in name) {
      return null
    }
    if ('\'' in name) {
      return null
    }
    return name to value
  }
}