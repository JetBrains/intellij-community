// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.util

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil.createTempExecutableScript
import com.intellij.execution.util.ExecUtil.envCommand
import com.intellij.execution.util.ExecUtil.envCommandArgs
import com.intellij.execution.util.ExecUtil.escapeAppleScriptArgument
import com.intellij.execution.util.ExecUtil.escapeUnixShellArgument
import com.intellij.execution.util.ExecUtil.getTerminalCommand
import com.intellij.execution.util.ExecUtil.hasTerminalApp
import com.intellij.execution.util.ExecUtil.osascriptPath
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.PathExecLazyValue
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.IdeUtilIoBundle
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
open class LocalSudoCommandProvider : SudoCommandProvider {
  override fun isAvailable(): Boolean {
    return true
  }

  override fun sudoCommand(wrappedCommand: GeneralCommandLine, prompt: @Nls String): GeneralCommandLine? {
    val command = mutableListOf(wrappedCommand.exePath)
    command += wrappedCommand.parametersList.list

    return when {
      SystemInfoRt.isWindows -> {
        val launcherExe = PathManager.findBinFileWithException("launcher.exe")
        GeneralCommandLine(listOf(launcherExe.toString(), wrappedCommand.exePath) + wrappedCommand.parametersList.parameters)
      }
      SystemInfoRt.isMac -> {
        val escapedCommand = command.joinToString(separator = " & \" \" & ") { escapeAppleScriptArgument(it) }
        val messageArg = " with prompt \"${StringUtil.escapeQuotes(prompt)}\""
        val escapedScript =
          "tell current application\n" +
          "   activate\n" +
          "   do shell script ${escapedCommand}${messageArg} with administrator privileges without altering line endings\n" +
          "end tell"
        GeneralCommandLine(osascriptPath, "-e", escapedScript)
      }
      // other UNIX
      hasGkSudo.get() -> {
        GeneralCommandLine(listOf("gksudo", "--message", prompt, "--") + envCommand(wrappedCommand) + command) //NON-NLS
      }
      hasKdeSudo.get() -> {
        GeneralCommandLine(listOf("kdesudo", "--comment", prompt, "--") + envCommand(wrappedCommand) + command) //NON-NLS
      }
      hasPkExec.get() -> {
        GeneralCommandLine(listOf("pkexec") + envCommand(wrappedCommand) + command) //NON-NLS
      }
      hasTerminalApp() -> {
        val escapedCommandLine = command.joinToString(separator = " ") { escapeUnixShellArgument(it) }

        @NlsSafe
        val escapedEnvCommand = when (val args = envCommandArgs(wrappedCommand)) {
          emptyList<String>() -> ""
          else -> "env " + args.joinToString(separator = " ") { escapeUnixShellArgument(it) } + " "
        }
        val script = createTempExecutableScript(
          "sudo", ".sh",
          "#!/bin/sh\n" +
          "echo " + escapeUnixShellArgument(prompt) + "\n" +
          "echo\n" +
          "sudo -- " + escapedEnvCommand + escapedCommandLine + "\n" +
          "STATUS=$?\n" +
          "echo\n" +
          "read -p \"Press Enter to close this window...\" TEMP\n" +
          "exit \$STATUS\n")
        GeneralCommandLine(getTerminalCommand(IdeUtilIoBundle.message("terminal.title.install"), script.absolutePath))
      }
      else -> null
    }
  }

  companion object {
    private val hasGkSudo = PathExecLazyValue.create("gksudo")
    private val hasKdeSudo = PathExecLazyValue.create("kdesudo")
    private val hasPkExec = PathExecLazyValue.create("pkexec")
  }
}