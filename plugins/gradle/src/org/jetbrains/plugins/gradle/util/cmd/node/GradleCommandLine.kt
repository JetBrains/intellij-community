// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.cmd.node

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.util.cmd.GradleCommandLineParser
import org.jetbrains.plugins.gradle.util.cmd.GradleCommandLineTokenizer

@ApiStatus.Experimental
data class GradleCommandLine(val tasks: GradleCommandLineTasks, val options: GradleCommandLineOptions) : GradleCommandLineNode {

  override val tokens: List<String> = tasks.tokens + options.tokens

  constructor(tasks: List<GradleCommandLineTask>, options: List<GradleCommandLineOption>)
    : this(GradleCommandLineTasks(tasks), GradleCommandLineOptions(options))

  companion object {
    @JvmStatic
    fun parse(commandLine: String): GradleCommandLine {
      return GradleCommandLineParser(GradleCommandLineTokenizer(commandLine)).parse()
    }

    @JvmStatic
    fun parse(commandLine: List<String>): GradleCommandLine {
      return GradleCommandLineParser(GradleCommandLineTokenizer(commandLine)).parse()
    }
  }
}