// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.util.execution.ParametersListUtil
import org.apache.commons.cli.Option
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsProvider

@ApiStatus.Internal
@ApiStatus.Experimental
class GradleCommandLine(val tasksAndArguments: List<String>, val scriptParameters: ScriptParameters) {
  class ScriptParameters(val options: List<String>, val vmOptions: List<String>) {
    override fun toString() = (options + vmOptions.map { "-D$it" }).joinToString(" ")
  }

  companion object {
    @JvmStatic
    fun parse(commandLine: String) = parse(ParametersListUtil.parse(commandLine, true, true))

    @JvmStatic
    fun parse(commandLine: List<String>): GradleCommandLine {
      val tasksAndArguments = ArrayList<String>()
      val options = ArrayList<String>()
      val vmOptions = ArrayList<String>()

      val allOptions = GradleCommandLineOptionsProvider.getSupportedOptions()
        .options.asSequence()
        .filterIsInstance<Option>()
        .flatMap { opt -> listOfNotNull(opt.opt?.let { "-$it" }, opt.longOpt?.let { "--$it" }) }
      for (token in commandLine) {
        when {
          token in allOptions -> options.add(token)
          token.startsWith("-P") -> options.add(token)
          token.startsWith("-D") -> vmOptions.add(token.removePrefix("-D"))
          else -> tasksAndArguments.add(token)
        }
      }

      return GradleCommandLine(tasksAndArguments, ScriptParameters(options, vmOptions))
    }
  }
}