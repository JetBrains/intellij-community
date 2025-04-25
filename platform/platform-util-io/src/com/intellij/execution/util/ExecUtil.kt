// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.util

import com.intellij.execution.CommandLineUtil
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.execution.sudo.SudoCommandProvider
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.PathExecLazyValue
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.execute
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.IdeUtilIoBundle
import com.intellij.util.io.SuperUserStatus
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.nio.file.Path

object ExecUtil {
  private val hasGnomeTerminal = PathExecLazyValue.create("gnome-terminal")
  @Suppress("SpellCheckingInspection")
  private val hasKdeTerminal = PathExecLazyValue.create("konsole")
  @Suppress("SpellCheckingInspection")
  private val hasUrxvt = PathExecLazyValue.create("urxvt")
  private val hasXTerm = PathExecLazyValue.create("xterm")

  @JvmStatic
  val osascriptPath: @NlsSafe String
    get() = "/usr/bin/osascript"

  @JvmStatic
  val openCommandPath: @NlsSafe String
    get() = "/usr/bin/open"

  @ApiStatus.Internal
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
  fun createTempExecutableScript(prefix: @NlsSafe String, suffix: @NlsSafe String, content: @NlsSafe String): java.io.File {
    val tempDir = java.io.File(PathManager.getTempPath())
    val tempFile = FileUtil.createTempFile(tempDir, prefix, suffix, true, true)
    FileUtil.writeToFile(tempFile, content.toByteArray(Charsets.UTF_8))
    if (!tempFile.setExecutable(true, true)) {
      throw ExecutionException(IdeUtilIoBundle.message("dialog.message.failed.to.make.temp.file.executable", tempFile))
    }
    return tempFile
  }

  @JvmStatic
  @Throws(ExecutionException::class)
  @RequiresBackgroundThread(generateAssertion = false)
  fun execAndGetOutput(commandLine: GeneralCommandLine): ProcessOutput =
    CapturingProcessHandler(commandLine).runProcess()

  @JvmStatic
  @Throws(ExecutionException::class)
  @RequiresBackgroundThread(generateAssertion = false)
  fun execAndGetOutput(commandLine: GeneralCommandLine, timeoutInMilliseconds: Int): ProcessOutput =
    CapturingProcessHandler(commandLine).runProcess(timeoutInMilliseconds)

  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false)
  fun execAndGetOutput(commandLine: GeneralCommandLine, stdin: String): String =
    CapturingProcessHandler(commandLine)
      .apply {
        addProcessListener(object : ProcessListener {
          override fun startNotified(event: ProcessEvent) {
            processInput.writer(commandLine.charset).use {
              it.write(stdin)
            }
          }
        })
      }
      .runProcess()
      .stdout

  @JvmStatic
  @RequiresBackgroundThread(generateAssertion = false)
  fun execAndReadLine(commandLine: GeneralCommandLine): String? = try {
    val process = commandLine.createProcess()
    BufferedReader(InputStreamReader(process.inputStream, commandLine.charset)).use { it.readLine() }
  }
  catch (e: ExecutionException) {
    logger<ExecUtil>().debug(e)
    null
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
  @ApiStatus.Internal
  @JvmStatic
  @Throws(ExecutionException::class, IOException::class)
  fun sudo(commandLine: GeneralCommandLine, prompt: @Nls String): Process = sudoCommand(commandLine, prompt).createProcess()

  @ApiStatus.Internal
  @JvmStatic
  @Throws(ExecutionException::class, IOException::class)
  fun sudoCommand(commandLine: GeneralCommandLine, prompt: @Nls String): GeneralCommandLine {
    if (SuperUserStatus.isSuperUser) {
      return commandLine
    }

    val sudoCommandLine = SudoCommandProvider.getInstance().sudoCommand(commandLine, prompt)
                          ?: throw UnsupportedOperationException("Cannot `sudo` on this system - no suitable utils found")

    return sudoCommandLine
      .withWorkingDirectory(commandLine.workingDirectory)
      .withEnvironment(commandLine.environment)
      .withParentEnvironmentType(commandLine.parentEnvironmentType)
      .withRedirectErrorStream(commandLine.isRedirectErrorStream)
  }

  @ApiStatus.Internal
  @JvmStatic
  @Throws(IOException::class, ExecutionException::class)
  fun sudoAndGetOutput(commandLine: GeneralCommandLine, prompt: @Nls String): ProcessOutput =
    execAndGetOutput(sudoCommand(commandLine, prompt))

  internal fun escapeAppleScriptArgument(arg: String): @NlsSafe String =
    if (arg == "&&") "\"$arg\"" // support multiple commands separated with &&
    else "quoted form of \"${arg.replace("\"", "\\\"").replace("\\", "\\\\")}\""

  @ApiStatus.Internal
  @Deprecated(
    "It is an oversimplified quoting. Prefer CommandLineUtil.posixQuote instead.",
    ReplaceWith("CommandLineUtil.posixQuote(arg)", "com.intellij.execution.CommandLineUtil.posixQuote"),
    level = DeprecationLevel.ERROR
  )
  @JvmStatic
  fun escapeUnixShellArgument(arg: String): String = "'${arg.replace("'", "'\"'\"'")}'"

  @ApiStatus.Internal
  @JvmStatic
  fun hasTerminalApp(): Boolean =
    SystemInfoRt.isWindows || SystemInfoRt.isMac || hasKdeTerminal.get() || hasGnomeTerminal.get() || hasUrxvt.get() || hasXTerm.get()

  @ApiStatus.Internal
  @JvmStatic
  @Suppress("SpellCheckingInspection")
  fun getTerminalCommand(@Nls(capitalization = Nls.Capitalization.Title) title: String?, command: String): List<@NlsSafe String> = when {
    SystemInfoRt.isWindows -> {
      listOf(CommandLineUtil.getWinShellName(), "/c", "start", GeneralCommandLine.inescapableQuote(title?.replace('"', '\'') ?: ""), command)
    }
    SystemInfoRt.isMac -> {
      val prefix = if (title != null) "\"echo -n \" & " + escapeAppleScriptArgument("\\0033]0;${title}\\007") + " & \" ; \" & " else ""
      val script = prefix + "\"clear ; exec \" & " + escapeAppleScriptArgument(command)
      listOf(osascriptPath, "-e", """
          |tell application "Terminal"
          |  activate
          |  do script $script
          |end tell
          """.trimMargin())
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

  @ApiStatus.Internal
  @JvmStatic
  fun EelExecApi.startProcessBlockingUsingEel(builder: ProcessBuilder, pty: LocalPtyOptions?, isPassParentEnvironment: Boolean): Process {
    val args = builder.command()
    val exe = args.first().let { exe -> runCatching { Path.of(exe).asEelPath().toString() }.getOrNull() ?: exe }
    val rest = args.subList(1, args.size)
    val env = (if (isPassParentEnvironment) runBlockingMaybeCancellable { fetchLoginShellEnvVariables() } else emptyMap()) + builder.environment()
    val workingDir = builder.directory()?.toPath()?.asEelPath()

    val options = execute(exe)
      .args(rest)
      .workingDirectory(workingDir)
      .env(env)
      .ptyOrStdErrSettings(pty?.run { EelExecApi.Pty(initialColumns, initialRows, !consoleMode) })

    return runBlockingMaybeCancellable {
      options.getOrThrow().convertToJavaProcess()
    }
  }
}
