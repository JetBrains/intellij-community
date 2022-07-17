// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.util.execution.ParametersListUtil
import org.apache.commons.cli.Option
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsProvider

@ApiStatus.Internal
@ApiStatus.Experimental
class GradleCommandLine(val tasksAndArguments: TasksAndArguments, val scriptParameters: ScriptParameters) {

  override fun toString() = tasksAndArguments.toString() + scriptParameters.toString()

  class Task(val name: String, val arguments: List<String>) {
    fun toList() = listOf(name) + arguments
    override fun toString() = toList().joinToString(" ")
  }

  class TasksAndArguments(val tasks: List<Task>) {
    fun toList() = tasks.flatMap(Task::toList)
    override fun toString() = toList().joinToString(" ")
  }

  class ScriptParameters(val options: List<String>) {
    override fun toString() = options.joinToString(" ")
  }

  companion object {
    @JvmStatic
    fun parse(commandLine: String) = parse(ParametersListUtil.parse(commandLine, true, true))

    @JvmStatic
    fun parse(commandLine: List<String>): GradleCommandLine {
      val state = ParserState(commandLine).apply {
        while (iterator.hasNext()) {
          val token = iterator.next()
          if (!tryParseOption(token)) {
            parseTask(token)
          }
        }
      }
      return state.getParsedCommandLine()
    }

    private fun ParserState.parseTask(token: String) {
      tasks.add(Task(token, emptyList()))
    }

    private fun ParserState.tryParseOption(token: String): Boolean {
      return tryParseOptionWithArguments(token) || tryParsePrefixOption(token)
    }

    private fun ParserState.tryParseOptionWithArguments(token: String): Boolean {
      val option = allOptions[token] ?: return false
      options.add(token)
      if (option.args > 0) {
        var unprocessedArguments = option.args
        while (iterator.hasNext() && unprocessedArguments != 0) {
          options.add(iterator.next())
          unprocessedArguments--
        }
      }
      else if (option.args == Option.UNLIMITED_VALUES) {
        while (iterator.hasNext()) {
          options.add(iterator.next())
        }
      }
      return true
    }

    private fun ParserState.tryParsePrefixOption(token: String): Boolean {
      if (shortOptions.any { it.value.args == 1 && token.startsWith(it.key) }) {
        options.add(token)
        return true
      }
      if (longOptions.any { it.value.args == 1 && token.startsWith(it.key + "=") }) {
        options.add(token)
        return true
      }
      return false
    }

    private class ParserState(commandLine: List<String>) {
      val iterator = commandLine.iterator()

      val tasks = ArrayList<Task>()
      val options = ArrayList<String>()

      val shortOptions = getAllOptions().asSequence()
        .filter { it.opt != null }
        .associateBy { "-${it.opt}" }
      val longOptions = getAllOptions().asSequence()
        .filter { it.longOpt != null }
        .associateBy { "--${it.longOpt}" }
      val allOptions = shortOptions + longOptions

      fun getParsedCommandLine(): GradleCommandLine {
        return GradleCommandLine(TasksAndArguments(tasks), ScriptParameters(options))
      }

      private fun getAllOptions() =
        GradleCommandLineOptionsProvider.OPTIONS.options +
        GradleCommandLineOptionsProvider.UNSUPPORTED_OPTIONS.options
    }
  }
}