// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config

import com.intellij.execution.CommandLineUtil
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.wsl.WSLCommandLineOptions
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.fs.getPath
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.asEelPath
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.utils.fetchLoginShellEnvVariablesBlocking
import com.intellij.vcs.VcsLocaleHelper
import git4idea.commands.GitHandler
import git4idea.i18n.GitBundle
import git4idea.repo.GitConfigKey
import git4idea.repo.GitConfigurationCache
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

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
  abstract fun convertFilePath(file: Path): String

  /**
   * Convert a file path, returned by git, to be used by IDE.
   */
  abstract fun convertFilePathBack(path: String, workingDir: Path): Path

  @Throws(ExecutionException::class)
  abstract fun patchCommandLine(handler: GitHandler, commandLine: GeneralCommandLine, executableContext: GitExecutableContext)

  @Throws(ExecutionException::class)
  abstract fun createBundledCommandLine(project: Project, vararg command: String): GeneralCommandLine

  abstract fun getLocaleEnv(): Map<String, String>

  abstract fun getModificationTime(): Long

  data class Local(override val exePath: String) : GitExecutable() {
    override val id: String = "local"
    override val isLocal: Boolean = true
    override fun toString(): String = exePath

    override fun convertFilePath(file: Path): String = file.absolutePathString()

    override fun convertFilePathBack(path: String, workingDir: Path): Path {
      if (SystemInfo.isWindows && path.startsWith(CYGDRIVE_PREFIX)) {
        val prefixSize = CYGDRIVE_PREFIX.length
        val localPath = path.substring(prefixSize, prefixSize + 1) + ":" + path.substring(prefixSize + 1)
        return Path(localPath)
      }
      return workingDir.resolve(path)
    }

    override fun patchCommandLine(handler: GitHandler, commandLine: GeneralCommandLine, executableContext: GitExecutableContext) {
      if (executableContext.isWithLowPriority) setupLowPriorityExecution(commandLine)
      if (executableContext.isWithNoTty) setupNoTtyExecution(commandLine, wait = false)
    }

    override fun getModificationTime(): Long {
      return getModificationTime(PathEnvironmentVariableUtil.getPathVariableValue(), SystemInfo.isMac, Path::of)
    }

    internal fun getModificationTime(pathVariable: String?, isMac: Boolean, toPath: (String) -> Path): Long {
      var filePath = exePath

      if (!filePath.contains(File.separator)) {
        var exeFile = PathEnvironmentVariableUtil.findInPath(filePath, pathVariable, null)
        if (exeFile != null) filePath = exeFile.path;
      }

      val executablePath = toPath(filePath);
      var modificationTime = Files.getLastModifiedTime(executablePath).toMillis()

      for (dependencyPath in GitExecutableDetector.getDependencyPaths(executablePath, isMac).map(toPath)) {
        runCatching {
          val depTime = Files.getLastModifiedTime(dependencyPath).toMillis()
          modificationTime = modificationTime.coerceAtLeast(depTime)
        }
      }

      return modificationTime;
    }

    internal fun doCreateBundledCommandLine(project: Project, isWindows: Boolean, vararg command: String): GeneralCommandLine {
      if (isWindows) {
        val bashPath = GitExecutableDetector.getBashExecutablePath(project, exePath)
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

    override fun createBundledCommandLine(project: Project, vararg command: String): GeneralCommandLine {
      return doCreateBundledCommandLine(project, SystemInfo.isWindows, *command)
    }

    private fun buildShellCommand(commandLine: List<String>): String = commandLine.joinToString(" ") { CommandLineUtil.posixQuote(it) }

    override fun getLocaleEnv(): Map<String, String> = VcsLocaleHelper.getDefaultLocaleEnvironmentVars("git")
  }

  data class Eel(override val exePath: @NonNls String, val eel: EelApi) : GitExecutable() {
    private val delegate = Local(exePath)

    override val id: @NonNls String = eel.descriptor.toString()
    override val isLocal: Boolean = eel.descriptor === LocalEelDescriptor

    override fun convertFilePath(file: Path): String {
      return if (isLocal) delegate.convertFilePath(file) else file.asEelPath().toString()
    }

    override fun getModificationTime(): Long {
      return delegate.getModificationTime(
        pathVariable = eel.exec.fetchLoginShellEnvVariablesBlocking()["PATH"],
        isMac = eel.platform is EelPlatform.Darwin,
        toPath = { eel.fs.getPath(it).asNioPath() }
      )
    }

    override fun convertFilePathBack(path: String, workingDir: Path): Path {
      return if (isLocal) delegate.convertFilePathBack(path, workingDir) else workingDir.resolve(path)
    }

    override fun patchCommandLine(handler: GitHandler, commandLine: GeneralCommandLine, executableContext: GitExecutableContext) {
      delegate.patchCommandLine(handler, commandLine, executableContext)
    }

    override fun createBundledCommandLine(project: Project, vararg command: String): GeneralCommandLine {
      return delegate.doCreateBundledCommandLine(project, eel.platform is EelPlatform.Windows, *command)
    }

    override fun getLocaleEnv(): Map<@NonNls String, @NonNls String> {
      return delegate.getLocaleEnv()
    }
  }

  data class Wsl(
    override val exePath: String,
    val distribution: WSLDistribution,
  ) : GitExecutable() {
    override val id: String = "wsl-${distribution.id}"
    override val isLocal: Boolean = false
    override fun toString(): String = "${distribution.presentableName}: $exePath"

    override fun getModificationTime(): Long {
      return 0
    }

    override fun convertFilePath(file: Path): String {
      val path = file.absolutePathString()

      // 'C:\Users\file.txt' -> '/mnt/c/Users/file.txt'
      val wslPath = distribution.getWslPath(file.toAbsolutePath())
      return wslPath ?: path
    }

    override fun convertFilePathBack(path: String, workingDir: Path): Path =
      // '/mnt/c/Users/file.txt' -> 'C:\Users\file.txt'
      Path(distribution.getWindowsPath(path))

    override fun patchCommandLine(handler: GitHandler, commandLine: GeneralCommandLine, executableContext: GitExecutableContext) {
      if (executableContext.isWithNoTty) {
        @Suppress("SpellCheckingInspection")
        setupNoTtyExecution(commandLine, wait = Registry.`is`("git.use.setsid.wait.for.wsl.ssh"))
      }

      patchWslExecutable(handler.project(), commandLine, executableContext.wslOptions)
    }

    override fun createBundledCommandLine(project: Project, vararg command: String): GeneralCommandLine {
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
          computeWslSupportedLocaleKey(distribution)
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

    override fun getModificationTime(): Long {
      return 0
    }

    override fun convertFilePath(file: Path): String = file.absolutePathString()
    override fun convertFilePathBack(path: String, workingDir: Path): Path = Path(path)

    override fun patchCommandLine(handler: GitHandler, commandLine: GeneralCommandLine, executableContext: GitExecutableContext) {
      throw ExecutionException(errorMessage)
    }

    override fun createBundledCommandLine(project: Project, vararg command: String): GeneralCommandLine {
      throw ExecutionException(errorMessage)
    }

    override fun getLocaleEnv(): Map<String, String> = emptyMap()
  }
}
