// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.cmd

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.execution.cmd.GradleCommandLineOptionsProvider
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLine
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineOption
import org.jetbrains.plugins.gradle.util.cmd.node.GradleCommandLineTask

@ApiStatus.Internal
class GradleCommandLineParser(private val tokenizer: GradleCommandLineTokenizer) {

  private val optionParser = GradleCommandLineOptionParser(ALL_OPTIONS, tokenizer)
  private val taskOptionParser = GradleCommandLineOptionParser(ALL_TASK_OPTIONS, tokenizer)

  fun parse(): GradleCommandLine {
    val tasks = ArrayList<GradleCommandLineTask>()
    val options = ArrayList<GradleCommandLineOption>()
    while (!tokenizer.isEof()) {
      while (!tokenizer.isEof()) {
        val option = optionParser.tryParseOption() ?: break
        options.add(option)
      }
      if (!tokenizer.isEof()) {
        tasks.add(parseTask())
      }
    }
    return GradleCommandLine(tasks, options)
  }

  private fun parseTask(): GradleCommandLineTask {
    val name = tokenizer.expected()
    val options = ArrayList<GradleCommandLineOption>()
    while (!tokenizer.isEof()) {
      val option = taskOptionParser.tryParseOption() ?: break
      options.add(option)
    }
    return GradleCommandLineTask(name, options)
  }

  companion object {
    private val ALL_OPTIONS =
      GradleCommandLineOptionsProvider.OPTIONS.options +
      GradleCommandLineOptionsProvider.UNSUPPORTED_OPTIONS.options

    private val ALL_TASK_OPTIONS =
      GradleCommandLineOptionsProvider.TASK_OPTIONS.options
  }
}