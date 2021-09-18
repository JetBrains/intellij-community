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
      val tasksAndArguments = ArrayList<Task>()
      val options = ArrayList<String>()

      val allOptions = GradleCommandLineOptionsProvider.getSupportedOptions()
        .options.asSequence()
        .filterIsInstance<Option>()
        .flatMap { opt -> listOfNotNull(opt.opt?.let { "-$it" }, opt.longOpt?.let { "--$it" }) }
      for (token in commandLine) {
        when {
          token in allOptions -> options.add(token)
          token.startsWith("-P") -> options.add(token)
          token.startsWith("-D") -> options.add(token)
          else -> tasksAndArguments.add(Task(token, emptyList()))
        }
      }

      return GradleCommandLine(TasksAndArguments(tasksAndArguments), ScriptParameters(options))
    }
  }
}