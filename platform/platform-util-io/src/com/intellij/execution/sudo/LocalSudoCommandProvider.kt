// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.sudo

import com.intellij.execution.CommandLineUtil
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.IdeUtilIoBundle
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
open class LocalSudoCommandProvider : SudoCommandProvider {
  override fun isAvailable(): Boolean = true

  override fun sudoCommand(wrappedCommand: GeneralCommandLine, prompt: @Nls String): GeneralCommandLine? {
    val command = listOf(wrappedCommand.exePath) + wrappedCommand.parametersList.list

    return when {
      OS.CURRENT == OS.Windows -> {
        val launcherExe = PathManager.findBinFileWithException("launcher.exe")
        GeneralCommandLine(listOf(launcherExe.toString(), wrappedCommand.exePath) + wrappedCommand.parametersList.parameters)
      }
      OS.CURRENT == OS.macOS -> {
        val escapedCommand = command.joinToString(separator = " & \" \" & ") { ExecUtil.escapeAppleScriptArgument(it) }
        val escapedPrompt = StringUtil.escapeQuotes(prompt)
        GeneralCommandLine(ExecUtil.osascriptPath, "-e", """
            tell current application
                activate
                do shell script ${escapedCommand} with prompt "${escapedPrompt}" with administrator privileges without altering line endings
            end tell""".trimIndent())
      }
      PathEnvironmentVariableUtil.isOnPath("gksudo") -> {
        GeneralCommandLine(listOf("gksudo", "--message", prompt, "--") + envCommand(wrappedCommand) + command)
      }
      PathEnvironmentVariableUtil.isOnPath("kdesudo") -> {
        GeneralCommandLine(listOf("kdesudo", "--comment", prompt, "--") + envCommand(wrappedCommand) + command)
      }
      PathEnvironmentVariableUtil.isOnPath("pkexec") -> {
        GeneralCommandLine(listOf("pkexec") + envCommand(wrappedCommand) + command)
      }
      ExecUtil.hasTerminalApp() -> {
        val escapedCommandLine = command.joinToString(separator = " ") { CommandLineUtil.posixQuote(it) }
        val escapedEnvCommand = envCommand(wrappedCommand).joinToString(separator = " ") { CommandLineUtil.posixQuote(it) }
        val script = ExecUtil.createTempExecutableScript("sudo", ".sh", """
            #!/bin/sh
            echo ${CommandLineUtil.posixQuote(prompt)}
            echo
            sudo -- ${escapedEnvCommand} ${escapedCommandLine}
            STATUS=$?
            echo
            read -p "Press Enter to close this window..." TEMP
            exit ${"$"}STATUS""".trimIndent())
        GeneralCommandLine(ExecUtil.getTerminalCommand(IdeUtilIoBundle.message("terminal.title.install"), script.absolutePath))
      }
      else -> null
    }
  }

  fun envCommand(commandLine: GeneralCommandLine): List<String> =
    when (val env = commandLine.environment) {
      emptyMap<String, String>() -> emptyList()
      else -> listOf("env") + env.map { entry -> "${entry.key}=${entry.value}" }
    }
}
