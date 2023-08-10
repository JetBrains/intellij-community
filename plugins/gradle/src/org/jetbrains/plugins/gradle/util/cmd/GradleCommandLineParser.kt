// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.cmd

import org.apache.commons.cli.Option
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsProvider
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLine
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineOption
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineOption.*
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineTask

@ApiStatus.Experimental
internal class GradleCommandLineParser(private val tokenizer: GradleCommandLineTokenizer) {

  fun parse(): GradleCommandLine {
    val tasks = ArrayList<GradleCommandLineTask>()
    val options = ArrayList<GradleCommandLineOption>()
    while (!tokenizer.isEof()) {
      val option = tryParseOption(ALL_OPTIONS)
      if (option != null) {
        options.add(option)
        continue
      }
      tasks.add(parseTask())
    }
    return GradleCommandLine(tasks, options)
  }

  private fun parseTask(): GradleCommandLineTask {
    val name = tokenizer.expected()
    val options = ArrayList<GradleCommandLineOption>()
    while (!tokenizer.isEof()) {
      val option = tryParseOption(ALL_TASK_OPTIONS) ?: break
      options.add(option)
    }
    return GradleCommandLineTask(name, options)
  }

  private fun tryParseOption(options: Collection<Option>): GradleCommandLineOption? {
    val shortOptionsIndex = options.asSequence()
      .filter { it.opt != null }
      .associateBy { "-${it.opt}" }
    val longOptionsIndex = options.asSequence()
      .filter { it.longOpt != null }
      .associateBy { "--${it.longOpt}" }

    val marker = tokenizer.mark()
    val option = tryParseOptionWithArguments(shortOptionsIndex + longOptionsIndex)
    if (option != null) return option
    tokenizer.rollback(marker)
    val shortOption = tryParseShortPrefixOption(shortOptionsIndex)
    if (shortOption != null) return shortOption
    tokenizer.rollback(marker)
    val longOption = tryParseLongPrefixOption(longOptionsIndex)
    if (longOption != null) return longOption
    tokenizer.rollback(marker)
    return null
  }

  private fun tryParseOptionWithArguments(options: Map<String, Option>): VarargNotation? {
    val name = tokenizer.expected()
    val option = options[name] ?: return null
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
    return VarargNotation(name, values)
  }

  private fun tryParseShortPrefixOption(options: Map<String, Option>): GradleCommandLineOption? {
    val token = tokenizer.expected()
    for ((name, option) in options) {
      if (option.args == 1 && token.startsWith(name)) {
        val value = token.removePrefix(name)
        if ('=' in value) {
          val propertyName = value.substringBefore('=')
          val propertyValue = value.substringAfter('=')
          return PropertyNotation(name, propertyName, propertyValue)
        }
        else {
          return ShortNotation(name, value)
        }
      }
    }
    return null
  }

  private fun tryParseLongPrefixOption(options: Map<String, Option>): GradleCommandLineOption? {
    val token = tokenizer.expected()
    for ((name, option) in options) {
      if (option.args == 1 && token.startsWith("$name=")) {
        return LongNotation(name, token.removePrefix("$name="))
      }
    }
    return null
  }

  companion object {
    private val ALL_OPTIONS =
      GradleCommandLineOptionsProvider.OPTIONS.options +
      GradleCommandLineOptionsProvider.UNSUPPORTED_OPTIONS.options

    private val ALL_TASK_OPTIONS =
      GradleCommandLineOptionsProvider.TASK_OPTIONS.options
  }
}