// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import org.apache.commons.cli.Option
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsProvider

@ApiStatus.Internal
@ApiStatus.Experimental
class GradleCommandLine(val tasks: List<String>, val scriptParameters: List<String>) {
  companion object {
    @JvmStatic
    fun parse(commandLine: List<String>): GradleCommandLine {
      val tasks = ArrayList<String>()
      val scriptParameters = ArrayList<String>()

      val allOptions = GradleCommandLineOptionsProvider.getSupportedOptions()
        .options.asSequence()
        .filterIsInstance<Option>()
        .flatMap { opt -> listOfNotNull(opt.opt?.let { "-$it" }, opt.longOpt?.let { "--$it" }) }
      for (token in commandLine) {
        when {
          token in allOptions -> scriptParameters.add(token)
          token.startsWith("-D") -> scriptParameters.add(token)
          token.startsWith("-P") -> scriptParameters.add(token)
          else -> tasks.add(token)
        }
      }
      return GradleCommandLine(tasks, scriptParameters)
    }
  }
}