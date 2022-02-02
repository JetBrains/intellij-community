// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config

import com.intellij.execution.CommandLineUtil
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.externalProcessAuthHelper.ScriptGenerator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.ContainerUtil
import externalApp.nativessh.NativeSshAskPassXmlRpcHandler
import git4idea.commands.GitHandler
import git4idea.editor.GitRebaseEditorXmlRpcHandler
import git4idea.http.GitAskPassXmlRpcHandler
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.io.File

sealed class GitExecutable {
  companion object {
    @NonNls
    private const val CYGDRIVE_PREFIX = "/cygdrive/"
  }

  abstract val id: @NonNls String
  abstract val exePath: @NonNls String
  abstract val isLocal: Boolean

  /**
   * Convert absolute file path into a form, that can be passed into executable arguments.
   */
  abstract fun convertFilePath(file: File): String

  /**
   * Convert file path, returned by git, to be used by IDE.
   */
  abstract fun convertFilePathBack(path: String, workingDir: File): File

  @Throws(ExecutionException::class)
  abstract fun patchCommandLine(handler: GitHandler, commandLine: GeneralCommandLine, executableContext: GitExecutableContext)

  @Throws(ExecutionException::class)
  abstract fun createBundledCommandLine(project: Project?, vararg command: String): GeneralCommandLine

  data class Local(override val exePath: String)
    : GitExecutable() {
    override val id: String = "local"
    override val isLocal: Boolean = true
    override fun toString(): String = exePath

    override fun convertFilePath(file: File): String = file.absolutePath

    override fun convertFilePathBack(path: String, workingDir: File): File {
      if (SystemInfo.isWindows && path.startsWith(CYGDRIVE_PREFIX)) {
        val prefixSize = CYGDRIVE_PREFIX.length
        val localPath = path.substring(prefixSize, prefixSize + 1) + ":" + path.substring(prefixSize + 1)
        return File(localPath)
      }
      return workingDir.resolve(path)
    }

    override fun patchCommandLine(handler: GitHandler, commandLine: GeneralCommandLine, executableContext: GitExecutableContext) {
      if (executableContext.isWithLowPriority) ExecUtil.setupLowPriorityExecution(commandLine)
      if (executableContext.isWithNoTty) ExecUtil.setupNoTtyExecution(commandLine)
    }

    override fun createBundledCommandLine(project: Project?, vararg command: String): GeneralCommandLine {
      if (SystemInfo.isWindows) {
        val bashPath = GitExecutableDetector.getBashExecutablePath(exePath)
                       ?: throw ExecutionException(GitBundle.message("git.executable.error.bash.not.found"))

        return GeneralCommandLine()
          .withExePath(bashPath)
          .withParameters("-c")
          .withParameters(buildShellCommand(command.toList()))
      }
      else {
        return GeneralCommandLine(*command)
      }
    }

    private fun buildShellCommand(commandLine: List<String>): String {
      return commandLine.joinToString(" ") { CommandLineUtil.posixQuote(it) }
    }
  }

  data class Wsl(override val exePath: String,
                 val distribution: WSLDistribution)
    : GitExecutable(), ScriptGenerator.CustomScriptCommandLineBuilder {
    override val id: String = "wsl-${distribution.id}"
    override val isLocal: Boolean = false
    override fun buildJavaCmd(cmd: StringBuilder) {
      val envs: List<String> = ContainerUtil.newArrayList(
        NativeSshAskPassXmlRpcHandler.IJ_SSH_ASK_PASS_HANDLER_ENV,
        NativeSshAskPassXmlRpcHandler.IJ_SSH_ASK_PASS_PORT_ENV,
        GitAskPassXmlRpcHandler.IJ_ASK_PASS_HANDLER_ENV,
        GitAskPassXmlRpcHandler.IJ_ASK_PASS_PORT_ENV,
        GitRebaseEditorXmlRpcHandler.IJ_EDITOR_HANDLER_ENV)
      cmd.append("export WSLENV=")
      cmd.append(StringUtil.join(envs,
                                 { it: String -> "$it/w" }, ":"))
      cmd.append("\n")

      cmd.append('"')
      val javaExecutable = File(String.format("%s\\bin\\java.exe", System.getProperty("java.home")))
      cmd.append(convertFilePath(javaExecutable))
      cmd.append('"')
    }

    override fun toString(): String = "${distribution.presentableName}: $exePath"

    override fun convertFilePath(file: File): String {
      val path = file.absolutePath

      // 'C:\Users\file.txt' -> '/mnt/c/Users/file.txt'
      val wslPath = distribution.getWslPath(path)
      if (wslPath != null) return wslPath

      // '\\wsl$\Ubuntu\home\user\file.txt' -> '/home/user/file.txt'
      val uncRoot = distribution.uncRoot
      if (FileUtil.isAncestor(uncRoot, file, false)) {
        return StringUtil.trimStart(FileUtil.toSystemIndependentName(path),
                                    FileUtil.toSystemIndependentName(uncRoot.path))
      }

      return path
    }

    override fun convertFilePathBack(path: String, workingDir: File): File {
      // '/mnt/c/Users/file.txt' -> 'C:\Users\file.txt'
      val localPath = distribution.getWindowsPath(path)
      if (localPath != null) return File(localPath)

      // '/home/user/file.txt' -> '\\wsl$\Ubuntu\home\user\file.txt'
      return File(distribution.uncRoot, path)
    }

    override fun patchCommandLine(handler: GitHandler, commandLine: GeneralCommandLine, executableContext: GitExecutableContext) {
      if (executableContext.isWithNoTty) {
        val executablePath = commandLine.exePath
        commandLine.exePath = "setsid"
        if (Registry.`is`("git.use.setsid.wait.for.wsl.ssh")) {
          commandLine.parametersList.prependAll("-w", executablePath)
        }
        else {
          commandLine.parametersList.prependAll(executablePath)
        }
      }

      // TODO: check that executable exists
      //var executable = exePath
      //if (withLowPriority) {
      //  commandLine.parametersList.prependAll("-n", "10", executable)
      //  executable = "/usr/bin/nice"
      //}
      //commandLine.exePath = executable

      patchWslExecutable(handler.project(), commandLine, executableContext.wslOptions)
    }

    override fun createBundledCommandLine(project: Project?, vararg command: String): GeneralCommandLine {
      val commandLine = GeneralCommandLine(*command)
      patchWslExecutable(project, commandLine, null)
      return commandLine
    }

    private fun patchWslExecutable(project: Project?, commandLine: GeneralCommandLine, wslOptions: WSLCommandLineOptions?) {
      val options = wslOptions ?: WSLCommandLineOptions()
      if (Registry.`is`("git.wsl.exe.executable.no.shell")) {
        options.isLaunchWithWslExe = true
        options.isExecuteCommandInShell = false
        options.isPassEnvVarsUsingInterop = true
      }
      distribution.patchCommandLine(commandLine, project, options)
    }
  }

  data class Unknown(override val id: String,
                     override val exePath: String,
                     val errorMessage: @Nls String)
    : GitExecutable() {
    override val isLocal: Boolean = false
    override fun toString(): String = "$id: $exePath"

    override fun convertFilePath(file: File): String = file.absolutePath
    override fun convertFilePathBack(path: String, workingDir: File): File = File(path)

    override fun patchCommandLine(handler: GitHandler, commandLine: GeneralCommandLine, executableContext: GitExecutableContext) {
      throw ExecutionException(errorMessage)
    }

    override fun createBundledCommandLine(project: Project?, vararg command: String): GeneralCommandLine {
      throw ExecutionException(errorMessage)
    }
  }
}
