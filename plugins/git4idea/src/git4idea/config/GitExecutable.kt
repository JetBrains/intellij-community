// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.execution.CommandLineUtil
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.vcs.VcsLocaleHelper
import git4idea.commands.GitHandler
import git4idea.i18n.GitBundle
import git4idea.repo.GitConfigKey
import git4idea.repo.GitConfigurationCache
import org.jetbrains.annotations.Nls
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

sealed class GitExecutable {
  private companion object {
    @Suppress("SpellCheckingInspection")
    private const val CYGDRIVE_PREFIX = "/cygdrive/"

    private const val NICE_PATH = "/usr/bin/nice"
    private val hasNice by lazy { Files.exists(Path.of(NICE_PATH)) }

    private const val SETSID_PATH = "/usr/bin/setsid"
    private val hasSetSid by lazy { Files.exists(Path.of(SETSID_PATH)) }

    private fun setupLowPriorityExecution(commandLine: GeneralCommandLine) {
      if (Registry.`is`("ide.allow.low.priority.process")) {
        if (SystemInfo.isWindows) {
          commandLine.withWrappingCommand(CommandLineUtil.getWinShellName(), "/c", "start", "/b", "/low", "/wait", GeneralCommandLine.inescapableQuote(""))
        }
        else if (hasNice) {
          commandLine.withWrappingCommand(NICE_PATH, "-n", "10")
        }
      }
    }

    private fun setupNoTtyExecution(commandLine: GeneralCommandLine, wait: Boolean) {
      if (SystemInfo.isLinux && hasSetSid) {
        if (wait) {
          commandLine.withWrappingCommand(SETSID_PATH, "-w")
        }
        else {
          commandLine.withWrappingCommand(SETSID_PATH)
        }
      }
    }
  }

  abstract val id: String
  abstract val exePath: String
  abstract val isLocal: Boolean

  /**
   * Convert an absolute file path into a form that can be passed into executable arguments.
   */
  abstract fun convertFilePath(file: File): String

  /**
   * Convert a file path, returned by git, to be used by IDE.
   */
  abstract fun convertFilePathBack(path: String, workingDir: File): File

  @Throws(ExecutionException::class)
  abstract fun patchCommandLine(handler: GitHandler, commandLine: GeneralCommandLine, executableContext: GitExecutableContext)

  @Throws(ExecutionException::class)
  abstract fun createBundledCommandLine(project: Project?, vararg command: String): GeneralCommandLine

  abstract fun getLocaleEnv(): Map<String, String>

  data class Local(override val exePath: String) : GitExecutable() {
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
      if (executableContext.isWithLowPriority) setupLowPriorityExecution(commandLine)
      if (executableContext.isWithNoTty) setupNoTtyExecution(commandLine, wait = true)
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

    private fun buildShellCommand(commandLine: List<String>): String = commandLine.joinToString(" ") { CommandLineUtil.posixQuote(it) }

    override fun getLocaleEnv(): Map<String, String> = VcsLocaleHelper.getDefaultLocaleEnvironmentVars("git")
  }

  data class Wsl(override val exePath: String, val distribution: WSLDistribution) : GitExecutable() {
    override val id: String = "wsl-${distribution.id}"
    override val isLocal: Boolean = false
    override fun toString(): String = "${distribution.presentableName}: $exePath"

    override fun convertFilePath(file: File): String {
      val path = file.absolutePath

      // 'C:\Users\file.txt' -> '/mnt/c/Users/file.txt'
      val wslPath = distribution.getWslPath(file.toPath().toAbsolutePath())
      return wslPath ?: path
    }

    override fun convertFilePathBack(path: String, workingDir: File): File =
      // '/mnt/c/Users/file.txt' -> 'C:\Users\file.txt'
      File(distribution.getWindowsPath(path))

    override fun patchCommandLine(handler: GitHandler, commandLine: GeneralCommandLine, executableContext: GitExecutableContext) {
      if (executableContext.isWithNoTty) {
        @Suppress("SpellCheckingInspection")
        setupNoTtyExecution(commandLine, wait = Registry.`is`("git.use.setsid.wait.for.wsl.ssh"))
      }

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
      else {
        options.isExecuteCommandInLoginShell = Registry.`is`("git.wsl.exe.executable.login.shell")
      }
      distribution.patchCommandLine(commandLine, project, options)
    }

    override fun getLocaleEnv(): Map<String, String> {
      if (Registry.`is`("git.wsl.exe.executable.detect.lang.by.env")) {
        val userLocale = VcsLocaleHelper.getEnvFromRegistry("git")
        if (userLocale != null) return userLocale

        val envMap = GitConfigurationCache.getInstance().computeCachedValue(WslSupportedLocaleKey(distribution)) {
          coroutineToIndicator {
            computeWslSupportedLocaleKey(distribution)
          }
        }
        if (envMap != null) return envMap
      }

      return VcsLocaleHelper.getDefaultLocaleEnvironmentVars("git")
    }

    private data class WslSupportedLocaleKey(val distribution: WSLDistribution) : GitConfigKey<Map<String, String>?>

    private fun computeWslSupportedLocaleKey(distribution: WSLDistribution): Map<String, String>? {
      val knownLocales = listOf(VcsLocaleHelper.EN_UTF_LOCALE, VcsLocaleHelper.C_UTF_LOCALE)

      val env = distribution.getEnvironmentVariable("LANG")
      if (env != null) {
        val envLocale = VcsLocaleHelper.findMatchingLocale(env, knownLocales)
        if (envLocale != null) {
          logger<WslSupportedLocaleKey>().debug("Detected locale by ENV: $env")
          return envLocale
        }
      }

      try {
        val wslCommandLineOptions = WSLCommandLineOptions().setExecuteCommandInLoginShell(false)
        val locales = distribution.executeOnWsl(listOf("locale", "-a"), wslCommandLineOptions, 10_000, null).stdout
        val systemLocales = locales.lineSequence().map { it.trim() }.filter { it.isNotBlank() }
        for (locale in systemLocales) {
          val someLocale = VcsLocaleHelper.findMatchingLocale(locale, knownLocales)
          if (someLocale != null) {
            logger<WslSupportedLocaleKey>().debug("Detected locale from available: $env")
            return someLocale
          }
        }
      }
      catch (e: ExecutionException) {
        logger<GitConfigurationCache>().warn(e)
        return null
      }

      return null
    }
  }

  data class Unknown(override val id: String, override val exePath: String, val errorMessage: @Nls String) : GitExecutable() {
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

    override fun getLocaleEnv(): Map<String, String> = emptyMap()
  }
}
