// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.util

import com.intellij.execution.CommandLineUtil
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.PathExecLazyValue
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus
import java.io.*
import java.nio.charset.Charset

object ExecUtil {
  private val hasGkSudo = PathExecLazyValue("gksudo")
  private val hasKdeSudo = PathExecLazyValue("kdesudo")
  private val hasPkExec = PathExecLazyValue("pkexec")
  private val hasGnomeTerminal = PathExecLazyValue("gnome-terminal")
  private val hasKdeTerminal = PathExecLazyValue("konsole")
  private val hasUrxvt = PathExecLazyValue("urxvt")
  private val hasXTerm = PathExecLazyValue("xterm")
  private val hasSetsid = PathExecLazyValue("setsid")

  private const val nicePath = "/usr/bin/nice"
  private val hasNice by lazy { File(nicePath).exists() }

  @JvmStatic
  val osascriptPath: String
    get() = "/usr/bin/osascript"

  @JvmStatic
  val openCommandPath: String
    get() = "/usr/bin/open"

  @JvmStatic
  val windowsShellName: String
    get() = CommandLineUtil.getWinShellName()

  @JvmStatic
  @Throws(IOException::class)
  fun loadTemplate(loader: ClassLoader, templateName: String, variables: Map<String, String>?): String {
    val stream = loader.getResourceAsStream(templateName) ?: throw IOException("Template '$templateName' not found by $loader")

    val template = FileUtil.loadTextAndClose(InputStreamReader(stream, Charsets.UTF_8))
    if (variables == null || variables.isEmpty()) {
      return template
    }

    val buffer = StringBuilder(template)
    for ((name, value) in variables) {
      val pos = buffer.indexOf(name)
      if (pos >= 0) {
        buffer.replace(pos, pos + name.length, value)
      }
    }
    return buffer.toString()
  }

  @JvmStatic
  @Throws(IOException::class, ExecutionException::class)
  fun createTempExecutableScript(prefix: String, suffix: String, content: String): File {
    val tempDir = File(PathManager.getTempPath())
    val tempFile = FileUtil.createTempFile(tempDir, prefix, suffix, true, true)
    FileUtil.writeToFile(tempFile, content.toByteArray(Charsets.UTF_8))
    if (!tempFile.setExecutable(true, true)) {
      throw ExecutionException("Failed to make temp file executable: $tempFile")
    }
    return tempFile
  }

  @JvmStatic
  @Throws(ExecutionException::class)
  fun execAndGetOutput(commandLine: GeneralCommandLine): ProcessOutput {
    return CapturingProcessHandler(commandLine).runProcess()
  }

  @JvmStatic
  @Throws(ExecutionException::class)
  fun execAndGetOutput(commandLine: GeneralCommandLine, timeoutInMilliseconds: Int): ProcessOutput {
    return CapturingProcessHandler(commandLine).runProcess(timeoutInMilliseconds)
  }

  @JvmStatic
  fun execAndReadLine(commandLine: GeneralCommandLine): String? {
    return try {
      readFirstLine(commandLine.createProcess().inputStream,
        commandLine.charset)
    }
    catch (e: ExecutionException) {
      Logger.getInstance(ExecUtil::class.java).debug(e)
      null
    }
  }

  @JvmStatic
  fun readFirstLine(stream: InputStream, cs: Charset?): String? {
    return try {
      BufferedReader(if (cs == null) InputStreamReader(stream) else InputStreamReader(stream, cs)).use { it.readLine() }
    }
    catch (e: IOException) {
      Logger.getInstance(ExecUtil::class.java).debug(e)
      null
    }
  }

  /**
   * Run the command with superuser privileges using safe escaping and quoting.
   *
   * No shell substitutions, input/output redirects, etc. in the command are applied.
   *
   * @param commandLine the command line to execute
   * @param prompt the prompt string for the users (not used on Windows)
   * @return the results of running the process
   */
  @JvmStatic
  @Throws(ExecutionException::class, IOException::class)
  fun sudo(commandLine: GeneralCommandLine, prompt: String): Process {
    return sudoCommand(commandLine, prompt).createProcess()
  }

  @JvmStatic
  @Throws(ExecutionException::class, IOException::class)
  fun sudoCommand(commandLine: GeneralCommandLine, prompt: String): GeneralCommandLine {
    if (SystemInfo.isUnix && "root" == System.getenv("USER")) {
      return commandLine
    }

    val command = mutableListOf(commandLine.exePath)
    command += commandLine.parametersList.list

    val sudoCommandLine = when {
      SystemInfo.isWinVistaOrNewer -> {
        val env = commandLine.effectiveEnvironment
        val fileName = if (env.isNotEmpty()) {
          val file = FileUtil.createTempFile("sudoVars", ".env", true)
          var writer = PrintWriter(file)
          for ((name, value) in env) {
            writer.write("$name=$value")
          }
          writer.close()
          file.absolutePath
        }
        else {
          // a special value that is interpreted by elevator.c as "there's no env file"
          "-"
        }

        val launcherExe = PathManager.findBinFileWithException("launcher.exe")
        GeneralCommandLine(listOf(launcherExe.toString(), fileName, commandLine.exePath) + commandLine.parametersList.parameters)
      }
      SystemInfo.isWindows -> {
        throw UnsupportedOperationException("Executing as Administrator is only available in Windows Vista or newer")
      }
      SystemInfo.isMac -> {
        val escapedCommand = StringUtil.join(command, {
          escapeAppleScriptArgument(it)
        }, " & \" \" & ")
        val messageArg = if (SystemInfo.isMacOSYosemite) " with prompt \"${StringUtil.escapeQuotes(prompt)}\"" else ""
        val escapedScript =
          "tell current application\n" +
          "   activate\n" +
          "   do shell script ${escapedCommand}${messageArg} with administrator privileges without altering line endings\n" +
          "end tell"
        GeneralCommandLine(osascriptPath, "-e", escapedScript)
      }
      // other UNIX
      hasGkSudo.value -> {
        GeneralCommandLine(listOf("gksudo", "--message", prompt, "--") + envCommand(commandLine) + command)
      }
      hasKdeSudo.value -> {
        GeneralCommandLine(listOf("kdesudo", "--comment", prompt, "--") + envCommand(commandLine) + command)
      }
      hasPkExec.value -> {
        GeneralCommandLine(listOf("pkexec") + envCommand(commandLine) + command)
      }
      hasTerminalApp() -> {
        val escapedCommandLine = StringUtil.join(command, {
          escapeUnixShellArgument(it)
        }, " ")
        val escapedEnvCommand = when (val args = envCommandArgs(commandLine)) {
          emptyList<String>() -> ""
          else -> "env " + StringUtil.join(args, {
            escapeUnixShellArgument(it)
          }, " ") + " "
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
        GeneralCommandLine(getTerminalCommand("Install", script.absolutePath))
      }
      else -> {
        throw UnsupportedOperationException("Cannot `sudo` on this system - no suitable utils found")
      }
    }

    val parentEnvType = if (SystemInfo.isWinVistaOrNewer)
      GeneralCommandLine.ParentEnvironmentType.NONE
    else
      commandLine.parentEnvironmentType
    return sudoCommandLine
      .withWorkDirectory(commandLine.workDirectory)
      .withEnvironment(commandLine.environment)
      .withParentEnvironmentType(parentEnvType)
      .withRedirectErrorStream(commandLine.isRedirectErrorStream)
  }

  private fun envCommand(commandLine: GeneralCommandLine): List<String> =
    when (val args = envCommandArgs(commandLine)) {
      emptyList<String>() -> emptyList()
      else -> listOf("env") + args
    }

  private fun envCommandArgs(commandLine: GeneralCommandLine): List<String> =
    // sudo doesn't pass parent process environment for security reasons,
    // for the same reasons we pass only explicitly configured env variables
    when (val env = commandLine.environment) {
      emptyMap<String, String>() -> emptyList()
      else -> env.map { entry -> "${entry.key}=${entry.value}" }
    }

  @JvmStatic
  @Throws(IOException::class, ExecutionException::class)
  fun sudoAndGetOutput(commandLine: GeneralCommandLine, prompt: String): ProcessOutput =
    execAndGetOutput(sudoCommand(commandLine, prompt))

  private fun escapeAppleScriptArgument(arg: String) = "quoted form of \"${arg.replace("\"", "\\\"")}\""

  @JvmStatic
  fun escapeUnixShellArgument(arg: String): String = "'${arg.replace("'", "'\"'\"'")}'"

  @JvmStatic
  fun hasTerminalApp(): Boolean =
    SystemInfo.isWindows || SystemInfo.isMac || hasKdeTerminal.value || hasGnomeTerminal.value || hasUrxvt.value || hasXTerm.value

  @JvmStatic
  fun getTerminalCommand(title: String?, command: String): List<String> = when {
    SystemInfo.isWindows -> {
      listOf(
        windowsShellName, "/c", "start", GeneralCommandLine.inescapableQuote(title?.replace('"', '\'') ?: ""), command)
    }
    SystemInfo.isMac -> {
      listOf(openCommandPath, "-a", "Terminal", command)
    }
    hasKdeTerminal.value -> {
      if (title != null) listOf("konsole", "-p", "tabtitle=\"${title.replace('"', '\'')}\"", "-e", command)
      else listOf("konsole", "-e", command)
    }
    hasGnomeTerminal.value -> {
      if (title != null) listOf("gnome-terminal", "-t", title, "-x", command)
      else listOf("gnome-terminal", "-x", command)
    }
    hasUrxvt.value -> {
      if (title != null) listOf("urxvt", "-title", title, "-e", command)
      else listOf("urxvt", "-e", command)
    }
    hasXTerm.value -> {
      if (title != null) listOf("xterm", "-T", title, "-e", command)
      else listOf("xterm", "-e", command)
    }
    else -> {
      throw UnsupportedOperationException("Unsupported OS/desktop: ${SystemInfo.OS_NAME}/${System.getenv("XDG_CURRENT_DESKTOP")}")
    }
  }

  /**
   * Wraps the commandline process with the OS specific utility
   * to mark the process to run with low priority.
   *
   * NOTE. Windows implementation does not return the original process exit code!
   */
  @JvmStatic
  fun setupLowPriorityExecution(commandLine: GeneralCommandLine) {
    if (canRunLowPriority()) {
      val executablePath = commandLine.exePath
      if (SystemInfo.isWindows) {
        commandLine.exePath = windowsShellName
        commandLine.parametersList.prependAll("/c", "start", "/b", "/low", "/wait", GeneralCommandLine.inescapableQuote(""), executablePath)
      }
      else {
        commandLine.exePath = nicePath
        commandLine.parametersList.prependAll("-n", "10", executablePath)
      }
    }
  }

  private fun canRunLowPriority() = Registry.`is`("ide.allow.low.priority.process") && (SystemInfo.isWindows || hasNice)

  @JvmStatic
  fun setupNoTtyExecution(commandLine: GeneralCommandLine) {
    if (SystemInfo.isLinux && hasSetsid.value) {
      val executablePath = commandLine.exePath
      commandLine.exePath = "setsid"
      commandLine.parametersList.prependAll(executablePath)
    }
  }

  //<editor-fold desc="Deprecated stuff.">
  @Deprecated("use {@link #execAndGetOutput(GeneralCommandLine)} instead")
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
  @JvmStatic
  @Throws(ExecutionException::class)
  fun execAndGetOutput(command: List<String>, workDir: String?): ProcessOutput {
    val commandLine = GeneralCommandLine(command).withWorkDirectory(workDir)
    return CapturingProcessHandler(commandLine).runProcess()
  }
  //</editor-fold>
}