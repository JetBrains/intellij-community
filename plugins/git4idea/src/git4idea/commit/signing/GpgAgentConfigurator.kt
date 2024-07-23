// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit.signing

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.idea.AppMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.SystemProperties
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.io.write
import git4idea.commands.GitScriptGenerator
import git4idea.commit.signing.GpgAgentPathsLocator.Companion.GPG_AGENT_CONF_BACKUP_FILE_NAME
import git4idea.commit.signing.GpgAgentPathsLocator.Companion.GPG_AGENT_CONF_FILE_NAME
import git4idea.commit.signing.GpgAgentPathsLocator.Companion.GPG_HOME_DIR
import git4idea.commit.signing.GpgAgentPathsLocator.Companion.PINENTRY_LAUNCHER_FILE_NAME
import git4idea.commit.signing.PinentryService.Companion.PINENTRY_USER_DATA_ENV
import git4idea.config.GitExecutable
import git4idea.config.GitExecutableListener
import git4idea.config.GitExecutableManager
import git4idea.gpg.PinentryApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.io.path.readLines

private val LOG = logger<GpgAgentConfigurator>()

@Service(Service.Level.PROJECT)
internal class GpgAgentConfigurator(private val project: Project, cs: CoroutineScope): Disposable {
  companion object {
    const val GPG_AGENT_PINENTRY_PROGRAM_CONF_KEY = "pinentry-program"

    @JvmStatic
    fun isEnabled(executable: GitExecutable): Boolean {
      return Registry.`is`("git.commit.gpg.signing.enable.embedded.pinentry", false) &&
             ((AppMode.isRemoteDevHost() && SystemInfo.isUnix)
             || executable is GitExecutable.Wsl
             || application.isUnitTestMode)
    }
  }

  init {
    val connection = application.messageBus.connect(this)
    connection.subscribe(GitExecutableManager.TOPIC, GitExecutableListener { cs.launch { configure() }})
  }

  suspend fun configure() {
    withContext(Dispatchers.IO) { doConfigure() }
  }

  private fun createPathLocator(executor: GitExecutable): GpgAgentPathsLocator {
    if (executor is GitExecutable.Wsl) {
      return WslGpgAgentPathsLocator(executor)
    }
    return MacAndUnixGpgAgentPathsLocator()
  }

  private fun createGpgAgentExecutor(executor: GitExecutable): GpgAgentCommandExecutor {
    if (executor is GitExecutable.Wsl) {
      return WslGpgAgentCommandExecutor(project, executor)
    }
    return LocalGpgAgentCommandExecutor()
  }

  @VisibleForTesting
  internal fun doConfigure(pathLocator: GpgAgentPathsLocator? = null) {
    val executable = GitExecutableManager.getInstance().getExecutable(project)
    if (!isEnabled(executable)) return

    val gpgAgentPaths = pathLocator?.resolvePaths() ?: createPathLocator(executable).resolvePaths() ?: return
    val gpgAgentConf = gpgAgentPaths.gpgAgentConf
    var needBackup = gpgAgentConf.exists()
    if (!needBackup) {
      LOG.debug("Cannot locate $gpgAgentConf, creating new")
      gpgAgentConf.write("$GPG_AGENT_PINENTRY_PROGRAM_CONF_KEY ${gpgAgentPaths.gpgPinentryAppLauncherConfigPath}")
      restartAgent(executable)
      needBackup = false
    }

    val config = readConfig(gpgAgentConf)
    if (config.content.isEmpty()) {
      LOG.debug("Empty $gpgAgentConf, skipping pinentry program configuration")
      return
    }

    if (needBackup) {
      val gpgAgentConfBackup = gpgAgentPaths.gpgAgentConfBackup
      if (gpgAgentConfBackup.exists()) {
        LOG.debug("$gpgAgentConfBackup already exist, skipping configuration backup")
      }
      else if (backupExistingConfig(gpgAgentPaths, config)) {
        changePinentryProgram(gpgAgentPaths, config)
        restartAgent(executable)
      }
    }

    //always regenerate the launcher to be up to date (e.g., java.home could be changed between versions)
    generatePinentryLauncher(executable, gpgAgentPaths)
  }

  private fun readConfig(gpgAgentConf: Path): GpgAgentConfig {
    val config = mutableMapOf<String, String>()
    try {
      gpgAgentConf.readLines().forEach { line ->
        val (key, value) = line.split(' ')
        config[key] = value
      }
    }
    catch (e: IOException) {
      LOG.error("Cannot read $gpgAgentConf", e)
      return GpgAgentConfig(gpgAgentConf, emptyMap())
    }
    return GpgAgentConfig(gpgAgentConf, config)
  }

  private fun generatePinentryLauncher(executable: GitExecutable, gpgAgentPaths: GpgAgentPaths) {
    val gpgAgentConfBackup = gpgAgentPaths.gpgAgentConfBackup
    val pinentryFallback = when {
      gpgAgentConfBackup.exists() -> readConfig(gpgAgentConfBackup).content[GPG_AGENT_PINENTRY_PROGRAM_CONF_KEY]
      else -> null
    }
    if (pinentryFallback.isNullOrBlank()) {
      LOG.debug("Pinentry fallback not found in $gpgAgentConfBackup. Skip pinentry script generation.")
    }
    PinentryShellScriptLauncherGenerator(executable)
      .generate(project, gpgAgentPaths, pinentryFallback)
  }

  private fun backupExistingConfig(gpgAgentPaths: GpgAgentPaths, config: GpgAgentConfig): Boolean {
    val pinentryAppLauncherConfigPath = gpgAgentPaths.gpgPinentryAppLauncherConfigPath
    if (config.content[GPG_AGENT_PINENTRY_PROGRAM_CONF_KEY] == pinentryAppLauncherConfigPath) {
      return false
    }
    val gpgAgentConf = gpgAgentPaths.gpgAgentConf
    val gpgAgentConfBackup = gpgAgentPaths.gpgAgentConfBackup
    try {
      gpgAgentConf.copyTo(gpgAgentConfBackup, overwrite = true)
    }
    catch (e: IOException) {
      LOG.warn("Cannot backup config $gpgAgentConf to $gpgAgentConfBackup", e)
      return false
    }
    return true
  }

  private fun changePinentryProgram(gpgAgentPaths: GpgAgentPaths, config: GpgAgentConfig) {
    val pinentryAppLauncherConfigPath = gpgAgentPaths.gpgPinentryAppLauncherConfigPath
    val (configPath, configContent) = config
    try {
      FileUtil.writeToFile(configPath.toFile(), configContent.map { (key, value) ->
        if (key == GPG_AGENT_PINENTRY_PROGRAM_CONF_KEY) {
          "$key $pinentryAppLauncherConfigPath"
        }
        else {
          "$key $value"
        }
      }.joinToString(separator = "\n"))
    }
    catch (e: IOException) {
      LOG.error("Cannot change config $configPath", e)
    }
  }

  private fun restartAgent(executor: GitExecutable) {
    try {
      val output = createGpgAgentExecutor(executor).execute("gpg-connect-agent", "reloadagent /bye").lastOrNull()
      if (output == "OK") {
        LOG.debug("Gpg Agent restarted successfully")
      }
      else {
        LOG.warn("Gpg Agent restart failed, restart manually to apply config changes")
      }
    }
    catch (e: ExecutionException) {
      LOG.warn("Gpg Agent restart failed, restart manually to apply config changes", e)
    }
  }

  override fun dispose() {}
}

internal interface GpgAgentCommandExecutor {
  @RequiresBackgroundThread
  fun execute(command: String, vararg params: String): List<String>
}

private class LocalGpgAgentCommandExecutor : GpgAgentCommandExecutor {
  override fun execute(command: String, vararg params: String): List<String> {
    return CapturingProcessHandler
      .Silent(GeneralCommandLine(command).withParameters(*params))
      .runProcess(10000, true).stdoutLines
  }
}

internal interface GpgAgentPathsLocator {
  companion object {
    const val GPG_HOME_DIR = ".gnupg"
    const val GPG_AGENT_CONF_FILE_NAME = "gpg-agent.conf"
    const val GPG_AGENT_CONF_BACKUP_FILE_NAME = "gpg-agent.conf.bak"
    const val PINENTRY_LAUNCHER_FILE_NAME = "pinentry-ide.sh"
  }
  fun resolvePaths(): GpgAgentPaths?
}

private class MacAndUnixGpgAgentPathsLocator : GpgAgentPathsLocator {
  override fun resolvePaths(): GpgAgentPaths? {
    try {
      val gpgAgentHome = Paths.get(SystemProperties.getUserHome(), GPG_HOME_DIR)
      val gpgAgentConf = gpgAgentHome.resolve(GPG_AGENT_CONF_FILE_NAME)
      val gpgAgentConfBackup = gpgAgentHome.resolve(GPG_AGENT_CONF_BACKUP_FILE_NAME)
      val gpgPinentryAppLauncher = gpgAgentHome.resolve(PINENTRY_LAUNCHER_FILE_NAME)

      return GpgAgentPaths(gpgAgentHome, gpgAgentConf, gpgAgentConfBackup,
                           gpgPinentryAppLauncher, gpgPinentryAppLauncher.toAbsolutePath().toString())
    }
    catch (e: InvalidPathException) {
      LOG.warn("Cannot resolve path", e)
      return null
    }
  }
}

private class WslGpgAgentCommandExecutor(private val project: Project,
                                         private val executable: GitExecutable.Wsl) : GpgAgentCommandExecutor {
  override fun execute(command: String, vararg params: String): List<String> {
    val commandLine = executable.createBundledCommandLine(project, command).withParameters(*params)
    return CapturingProcessHandler
      .Silent(commandLine)
      .runProcess(10000, true).stdoutLines
  }
}

private class WslGpgAgentPathsLocator(private val executable: GitExecutable.Wsl) : GpgAgentPathsLocator {
  override fun resolvePaths(): GpgAgentPaths? {
    try {
      val gpgAgentHome = getWindowsAccessibleGpgHome(executable) ?: return null
      val gpgAgentConf = gpgAgentHome.resolve(GPG_AGENT_CONF_FILE_NAME)
      val gpgAgentConfBackup = gpgAgentHome.resolve(GPG_AGENT_CONF_BACKUP_FILE_NAME)
      val gpgPinentryAppLauncher = gpgAgentHome.resolve(PINENTRY_LAUNCHER_FILE_NAME)
      val pathToPinentryAppInWsl = (getPathInWslUserHome(executable) ?: return null) + "/$GPG_HOME_DIR/$PINENTRY_LAUNCHER_FILE_NAME"
      return GpgAgentPaths(gpgAgentHome, gpgAgentConf, gpgAgentConfBackup, gpgPinentryAppLauncher,
                           pathToPinentryAppInWsl)
    }
    catch (e: InvalidPathException) {
      LOG.warn("Cannot resolve path", e)
      return null
    }
  }

  private fun getPathInWslUserHome(executable: GitExecutable.Wsl): String? {
    val wslDistribution = executable.distribution
    val wslUserHomePath = wslDistribution.userHome?.trimEnd('/')
    if (wslUserHomePath == null) return null
    LOG.debug("User home path in WSL = $wslUserHomePath")

    return wslUserHomePath
  }

  private fun getWindowsAccessibleGpgHome(executable: GitExecutable.Wsl): Path? {
    val wslDistribution = executable.distribution
    val wslUserHomePath = getPathInWslUserHome(executable)
    if (wslUserHomePath != null) {
      return Path.of(wslDistribution.getWindowsPath(wslUserHomePath), GPG_HOME_DIR)
    }
    else {
      LOG.warn("Cannot resolve wsl user home path")
    }
    return null
  }
}

internal interface PinentryLauncherGenerator {
  val executable: GitExecutable
  fun getScriptTemplate(fallbackPinentryPath: String?): String

  fun generate(project: Project, gpgAgentPaths: GpgAgentPaths, fallbackPinentryPath: String?): Boolean {
    val path = gpgAgentPaths.gpgPinentryAppLauncher
    try {
      FileUtil.writeToFile(path.toFile(), getScriptTemplate(fallbackPinentryPath))
      val executable = executable
      if (executable is GitExecutable.Wsl) {
        val launcherConfigPath = gpgAgentPaths.gpgPinentryAppLauncherConfigPath
        WslGpgAgentCommandExecutor(project, executable).execute("chmod", "+x", launcherConfigPath)
      }
      else {
        NioFiles.setExecutable(path)
      }
    }
    catch (e: IOException) {
      LOG.warn("Cannot generate $path", e)
      return false
    }
    return true
  }

  fun getCommandLineParameters(): Array<String> {
    return if (LOG.isDebugEnabled) arrayOf("--log") else emptyArray()
  }
}

internal class PinentryShellScriptLauncherGenerator(override val executable: GitExecutable) :
  GitScriptGenerator(executable), PinentryLauncherGenerator {

  @Language("Shell Script")
  override fun getScriptTemplate(fallbackPinentryPath: String?): String {
    if (fallbackPinentryPath == null) {
      return """|#!/bin/sh
                |${addParameters(*getCommandLineParameters()).commandLine(PinentryApp::class.java, false)}
             """.trimMargin()
    }

    return """|#!/bin/sh
              |if [ -n "${'$'}$PINENTRY_USER_DATA_ENV" ]; then
              |  ${addParameters(*getCommandLineParameters()).commandLine(PinentryApp::class.java, false)}
              |else
              |  exec $fallbackPinentryPath "$@"
              |fi
           """.trimMargin()
  }
}

internal data class GpgAgentPaths(
  val gpgAgentHome: Path,
  val gpgAgentConf: Path,
  val gpgAgentConfBackup: Path,
  val gpgPinentryAppLauncher: Path,
  val gpgPinentryAppLauncherConfigPath: String,
)
private data class GpgAgentConfig(val path: Path, val content: Map<String, String>)

private class GpgAgentConfiguratorStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.service<GpgAgentConfigurator>().configure()
  }
}
