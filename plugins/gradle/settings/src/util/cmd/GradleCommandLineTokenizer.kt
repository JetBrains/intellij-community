// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.cmd

import com.intellij.util.execution.ParametersListUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class GradleCommandLineTokenizer(commandLine: List<String>) {

  private val commandLine = commandLine.filter { it.isNotEmpty() }
  private var index = -1

  constructor(commandLine: String) : this(ParametersListUtil.parse(commandLine, true, true))

  fun isEof(): Boolean {
    return (index + 1) >= commandLine.size
  }

  fun expected(): String {
    if ((index + 1) < commandLine.size) {
      index++
      return commandLine[index]
    }
    throw IllegalStateException("Unexpected EOL for $index and $commandLine")
  }

  fun mark(): Int = index

  fun rollback(marker: Int) {
    index = marker
  }
}