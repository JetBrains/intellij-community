// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.util

import com.intellij.execution.CommandLineUtil
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.PathExecLazyValue
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.IdeUtilIoBundle
import com.intellij.util.io.SuperUserStatus
import org.jetbrains.annotations.Nls
import java.io.*
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path

object ExecUtil {
  private val hasGkSudo = PathExecLazyValue.create("gksudo")
  private val hasKdeSudo = PathExecLazyValue.create("kdesudo")
  private val hasPkExec = PathExecLazyValue.create("pkexec")
  private val hasGnomeTerminal = PathExecLazyValue.create("gnome-terminal")
  private val hasKdeTerminal = PathExecLazyValue.create("konsole")
  private val hasUrxvt = PathExecLazyValue.create("urxvt")
  private val hasXTerm = PathExecLazyValue.create("xterm")
  private val hasSetsid = PathExecLazyValue.create("setsid")

  @field:NlsSafe
  private const val nicePath = "/usr/bin/nice"
  private val hasNice by lazy { Files.exists(Path.of(nicePath)) }

  @JvmStatic
  val osascriptPath: String
    @NlsSafe
    get() = "/usr/bin/osascript"

  @JvmStatic
  val openCommandPath: String
    @NlsSafe
    get() = "/usr/bin/open"

  @JvmStatic
  val windowsShellName: String
    get() = CommandLineUtil.getWinShellName()

  @JvmStatic
  @Throws(IOException::class)
  fun loadTemplate(loader: ClassLoader, templateName: String, variables: Map<String, String>?): String {
    val stream = loader.getResourceAsStream(templateName) ?: throw IOException("Template '$templateName' not found by $loader")
    val template = stream.use { it.readAllBytes().decodeToString() }
    if (variables.isNullOrEmpty()) {
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
  fun createTempExecutableScript(@NlsSafe prefix: String, @NlsSafe suffix: String, @NlsSafe content: String): File {
    val tempDir = File(PathManager.getTempPath())
    val tempFile = FileUtil.createTempFile(tempDir, prefix, suffix, true, true)
    FileUtil.writeToFile(tempFile, content.toByteArray(Charsets.UTF_8))
    if (!tempFile.setExecutable(true, true)) {
      throw ExecutionException(IdeUtilIoBundle.message("dialog.message.failed.to.make.temp.file.executable", tempFile))
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
  fun execAndGetOutput(commandLine: GeneralCommandLine, stdin: String): String {
    return CapturingProcessHandler(commandLine).also { processHandler ->
      processHandler.addProcessListener(object : ProcessAdapter() {
        override fun startNotified(event: ProcessEvent) {
          processHandler.processInput.writer(commandLine.charset).use {
            it.write(stdin)
          }
        }
      })
    }.runProcess().stdout
  }

  @JvmStatic
  fun execAndReadLine(commandLine: GeneralCommandLine): String? {
    return try {
      readFirstLine(commandLine.createProcess().inputStream, commandLine.charset)
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
  fun sudo(commandLine: GeneralCommandLine, prompt: @Nls String): Process {
    return sudoCommand(commandLine, prompt).createProcess()
  }

  @JvmStatic
  @Throws(ExecutionException::class, IOException::class)
  fun sudoCommand(commandLine: GeneralCommandLine, prompt: @Nls String): GeneralCommandLine {
    if (SuperUserStatus.isSuperUser) {
      return commandLine
    }

    val command = mutableListOf(commandLine.exePath)
    command += commandLine.parametersList.list

    val sudoCommandLine = when {
      SystemInfoRt.isWindows -> {
        val launcherExe = PathManager.findBinFileWithException("launcher.exe")
        GeneralCommandLine(listOf(launcherExe.toString(), commandLine.exePath) + commandLine.parametersList.parameters)
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
        GeneralCommandLine(listOf("gksudo", "--message", prompt, "--") + envCommand(commandLine) + command)//NON-NLS
      }
      hasKdeSudo.get() -> {
        GeneralCommandLine(listOf("kdesudo", "--comment", prompt, "--") + envCommand(commandLine) + command)//NON-NLS
      }
      hasPkExec.get() -> {
        GeneralCommandLine(listOf("pkexec") + envCommand(commandLine) + command)//NON-NLS
      }
      hasTerminalApp() -> {
        val escapedCommandLine = command.joinToString(separator = " ") { escapeUnixShellArgument(it) }
        @NlsSafe
        val escapedEnvCommand = when (val args = envCommandArgs(commandLine)) {
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
      else -> {
        throw UnsupportedOperationException("Cannot `sudo` on this system - no suitable utils found")
      }
    }

    val parentEnvType = if (SystemInfoRt.isWindows) GeneralCommandLine.ParentEnvironmentType.NONE else commandLine.parentEnvironmentType
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
  fun sudoAndGetOutput(commandLine: GeneralCommandLine, prompt: @Nls String): ProcessOutput =
    execAndGetOutput(sudoCommand(commandLine, prompt))

  @NlsSafe
  private fun escapeAppleScriptArgument(arg: String) = "quoted form of \"${arg.replace("\"", "\\\"").replace("\\", "\\\\")}\""

  @JvmStatic
  fun escapeUnixShellArgument(arg: String): String = "'${arg.replace("'", "'\"'\"'")}'"

  @JvmStatic
  fun hasTerminalApp(): Boolean {
    return SystemInfoRt.isWindows || SystemInfoRt.isMac ||
           hasKdeTerminal.get() || hasGnomeTerminal.get() || hasUrxvt.get() || hasXTerm.get()
  }

  @NlsSafe
  @JvmStatic
  fun getTerminalCommand(@Nls(capitalization = Nls.Capitalization.Title) title: String?, command: String): List<String> {
    return when {
      SystemInfoRt.isWindows -> {
        listOf(windowsShellName, "/c", "start", GeneralCommandLine.inescapableQuote(title?.replace('"', '\'') ?: ""), command)
      }
      SystemInfoRt.isMac -> {
        var script = "\"clear ; exec \" & " + escapeAppleScriptArgument(command)
        if (title != null)
          script = "\"echo -n \" & " + escapeAppleScriptArgument("\\0033]0;$title\\007") + " & \" ; \" & " + script

        // At this point, the script variable will contain a shell script line like this:
        // clear ; exec $command                                  # in case no title is provided
        // echo -n "\\0033]0;$title\\007" ; clear ; exec $command # in case title was provided

        val escapedScript = """
          |tell application "Terminal"
          |  activate
          |  do script $script
          |end tell
          """.trimMargin()
        listOf(osascriptPath, "-e", escapedScript)
      }
      hasKdeTerminal.get() -> {
        if (title != null) listOf("konsole", "-p", "tabtitle=\"${title.replace('"', '\'')}\"", "-e", command)
        else listOf("konsole", "-e", command)
      }
      hasGnomeTerminal.get() -> {
        if (title != null) listOf("gnome-terminal", "-t", title, "-x", command)
        else listOf("gnome-terminal", "-x", command)
      }
      hasUrxvt.get() -> {
        if (title != null) listOf("urxvt", "-title", title, "-e", command)
        else listOf("urxvt", "-e", command)
      }
      hasXTerm.get() -> {
        if (title != null) listOf("xterm", "-T", title, "-e", command)
        else listOf("xterm", "-e", command)
      }
      else -> {
        throw UnsupportedOperationException("Unsupported OS/desktop: ${SystemInfoRt.OS_NAME}/${System.getenv("XDG_CURRENT_DESKTOP")}")
      }
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
      if (SystemInfoRt.isWindows) {
        commandLine.exePath = windowsShellName
        commandLine.parametersList.prependAll("/c", "start", "/b", "/low", "/wait", GeneralCommandLine.inescapableQuote(""), executablePath)
      }
      else {
        commandLine.exePath = nicePath
        commandLine.parametersList.prependAll("-n", "10", executablePath)
      }
    }
  }

  private fun canRunLowPriority() = Registry.`is`("ide.allow.low.priority.process") && (SystemInfoRt.isWindows || hasNice)

  @JvmStatic
  fun setupNoTtyExecution(commandLine: GeneralCommandLine) {
    if (SystemInfoRt.isLinux && hasSetsid.get()) {
      val executablePath = commandLine.exePath
      commandLine.exePath = "setsid"
      commandLine.parametersList.prependAll(executablePath)
    }
  }
}
