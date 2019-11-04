// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.execution.wsl.WSLDistribution
import git4idea.commands.GitHandler
import java.io.File

sealed class GitExecutable {
  abstract val exePath: String
  abstract val isLocal: Boolean

  /**
   * Convert absolute file path into a form, that can be passed into executable arguments.
   */
  abstract fun convertFilePath(file: File): String
  abstract fun patchCommandLine(handler: GitHandler, commandLine: GeneralCommandLine, withLowPriority: Boolean, withNoTty: Boolean)

  data class Local(override val exePath: String)
    : GitExecutable() {
    override val isLocal: Boolean = true
    override fun toString(): String = exePath

    override fun convertFilePath(file: File): String = file.absolutePath

    override fun patchCommandLine(handler: GitHandler, commandLine: GeneralCommandLine, withLowPriority: Boolean, withNoTty: Boolean) {
      if (withLowPriority) ExecUtil.setupLowPriorityExecution(commandLine)
      if (withNoTty) ExecUtil.setupNoTtyExecution(commandLine)
    }
  }

  data class Wsl(override val exePath: String,
                 val distribution: WSLDistribution)
    : GitExecutable() {
    override val isLocal: Boolean = false
    override fun toString(): String = "${distribution.presentableName}: $exePath"

    override fun convertFilePath(file: File): String {
      val path = file.absolutePath
      return distribution.getWslPath(path) ?: path
    }

    override fun patchCommandLine(handler: GitHandler, commandLine: GeneralCommandLine, withLowPriority: Boolean, withNoTty: Boolean) {
      // Handling 'withNoTty' is not needed, as tty can't leak into WSL executable

      // TODO: check that executable exists
      //var executable = exePath
      //if (withLowPriority) {
      //  commandLine.parametersList.prependAll("-n", "10", executable)
      //  executable = "/usr/bin/nice"
      //}
      //commandLine.exePath = executable

      distribution.patchCommandLine(commandLine, handler.project(), null, false)
    }
  }
}
