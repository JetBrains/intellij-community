// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.util

import com.intellij.execution.CommandLineUtil
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.execution.sudo.SudoCommandProvider
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.PathExecLazyValue
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.getOrThrow
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.IdeUtilIoBundle
import com.intellij.util.io.SuperUserStatus
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.io.*
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.pathString

object ExecUtil {
  private val hasGnomeTerminal = PathExecLazyValue.create("gnome-terminal")
  @Suppress("SpellCheckingInspection")
  private val hasKdeTerminal = PathExecLazyValue.create("konsole")
  @Suppress("SpellCheckingInspection")
  private val hasUrxvt = PathExecLazyValue.create("urxvt")
  private val hasXTerm = PathExecLazyValue.create("xterm")
  @Suppress("SpellCheckingInspection")
  private val hasSetsid = PathExecLazyValue.create("setsid")

  private const val NICE_PATH = "/usr/bin/nice"
  private val hasNice by lazy { Files.exists(Path.of(NICE_PATH)) }

  @JvmStatic
  val osascriptPath: @NlsSafe String
    get() = "/usr/bin/osascript"

  @JvmStatic
  val openCommandPath: @NlsSafe String
    get() = "/usr/bin/open"

  @JvmStatic
  @Suppress("unused")
  val windowsShellName: String
    @Deprecated("Inline this property", level = DeprecationLevel.ERROR)
    get() = CommandLineUtil.getWinShellName()

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
  fun createTempExecutableScript(prefix: @NlsSafe String, suffix: @NlsSafe String, content: @NlsSafe String): File {
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
        addProcessListener(object : ProcessAdapter() {
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
    readFirstLine(commandLine.createProcess().inputStream, commandLine.charset)
  }
  catch (e: ExecutionException) {
    Logger.getInstance(ExecUtil::class.java).debug(e)
    null
  }

  @ApiStatus.Internal
  @RequiresBackgroundThread(generateAssertion = false)
  @JvmStatic
  fun readFirstLine(stream: InputStream, cs: Charset?): String? = try {
    BufferedReader(if (cs == null) InputStreamReader(stream) else InputStreamReader(stream, cs)).use { it.readLine() }
  }
  catch (e: IOException) {
    Logger.getInstance(ExecUtil::class.java).debug(e)
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

  internal fun escapeAppleScriptArgument(arg: String): @NlsSafe String = "quoted form of \"${arg.replace("\"", "\\\"").replace("\\", "\\\\")}\""

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

  /**
   * Wraps the commandline process with the OS-specific utility to mark the process to run with low priority.
   *
   * NOTE: Windows implementation does not return the original process exit code!
   */
  @ApiStatus.Internal
  @JvmStatic
  fun setupLowPriorityExecution(commandLine: GeneralCommandLine) {
    if (canRunLowPriority()) {
      val executablePath = commandLine.exePath
      if (SystemInfoRt.isWindows) {
        commandLine.withExePath(CommandLineUtil.getWinShellName())
        commandLine.parametersList.prependAll("/c", "start", "/b", "/low", "/wait", GeneralCommandLine.inescapableQuote(""), executablePath)
      }
      else {
        commandLine.withExePath(NICE_PATH)
        commandLine.parametersList.prependAll("-n", "10", executablePath)
      }
    }
  }

  private fun canRunLowPriority() = Registry.`is`("ide.allow.low.priority.process") && (SystemInfoRt.isWindows || hasNice)

  @ApiStatus.Internal
  @JvmStatic
  fun setupNoTtyExecution(commandLine: GeneralCommandLine) {
    if (SystemInfoRt.isLinux && hasSetsid.get()) {
      val executablePath = commandLine.exePath
      @Suppress("SpellCheckingInspection")
      commandLine.withExePath("setsid")
      commandLine.parametersList.prependAll(executablePath)
    }
  }

  @ApiStatus.Internal
  @JvmStatic
  fun EelExecApi.startProcessBlockingUsingEel(builder: ProcessBuilder, pty: LocalPtyOptions?): Process {
    val args = builder.command()
    val exe = args.first()
    val rest = args.subList(1, args.size)
    val env = builder.environment()
    val workingDir = builder.directory()?.toPath()?.asEelPath()

    val options = EelExecApi.ExecuteProcessOptions.Builder(exe)
      .args(rest)
      .workingDirectory(workingDir)
      .env(env)
      .ptyOrStdErrSettings(pty?.run { EelExecApi.Pty(initialColumns, initialRows, !consoleMode) })

    return runBlockingMaybeCancellable {
      execute(options.build()).getOrThrow().convertToJavaProcess()
    }
  }
}
