// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit.signing

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
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
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.util.SystemProperties
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import git4idea.commands.GitScriptGenerator
import git4idea.commit.signing.GpgAgentPathsLocator.Companion.GPG_AGENT_CONF_BACKUP_FILE_NAME
import git4idea.commit.signing.GpgAgentPathsLocator.Companion.GPG_AGENT_CONF_FILE_NAME
import git4idea.commit.signing.GpgAgentPathsLocator.Companion.GPG_HOME_DIR
import git4idea.commit.signing.GpgAgentPathsLocator.Companion.PINENTRY_LAUNCHER_FILE_NAME
import git4idea.commit.signing.PinentryService.Companion.PINENTRY_USER_DATA_ENV
import git4idea.commit.signing.PinentryService.PinentryData
import git4idea.config.GitExecutable
import git4idea.config.GitExecutableListener
import git4idea.config.GitExecutableManager
import git4idea.config.gpg.getGpgSignKeyCached
import git4idea.config.gpg.isGpgSignEnabledCached
import git4idea.gpg.PinentryApp
import git4idea.repo.GitConfigListener
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.io.path.readLines

private val LOG = logger<GpgAgentConfigurator>()

@Service(Service.Level.PROJECT)
internal class GpgAgentConfigurator(private val project: Project, private val cs: CoroutineScope): Disposable {
  companion object {
    const val GPG_AGENT_PINENTRY_PROGRAM_CONF_KEY = "pinentry-program"

    @JvmStatic
    fun isEnabled(project: Project, executable: GitExecutable): Boolean {
      return Registry.`is`("git.commit.gpg.signing.enable.embedded.pinentry", false) &&
             (SystemInfo.isUnix
              || executable is GitExecutable.Wsl
              || application.isUnitTestMode)
             && // do not configure Gpg Agent for roots without commit.gpgSign and user.signingkey enabled
             GitRepositoryManager.getInstance(project)
               .repositories.any { repository ->
                 isGpgSignEnabledCached(repository)
                 && getGpgSignKeyCached(repository) != null
               }

    }
  }

  private val updateLauncherQueue = MergingUpdateQueue("update pinentry launcher queue", 100, true, null, this, null, false)

  fun init() {
    val connection = application.messageBus.connect(this)
    connection.subscribe(GitExecutableManager.TOPIC, GitExecutableListener {
      project.service<GpgAgentConfigurationNotificator>().proposeCustomPinentryAgentConfiguration(isSuggestion = false)
      emitUpdateLauncherEvent()
    })
    project.messageBus.connect(this).subscribe(GitConfigListener.TOPIC, object: GitConfigListener {
      override fun notifyConfigChanged(repository: GitRepository) {
        project.service<GpgAgentConfigurationNotificator>().proposeCustomPinentryAgentConfiguration(isSuggestion = false)
        emitUpdateLauncherEvent()
      }
    })

    emitUpdateLauncherEvent()
  }

  @RequiresBackgroundThread
  fun isConfigured(project: Project): Boolean {
    val executable = GitExecutableManager.getInstance().getExecutable(project)
    val gpgAgentPaths = createPathLocator(executable).resolvePaths() ?: return false

    return gpgAgentPaths.gpgPinentryAppLauncher.exists()
           && readConfig(gpgAgentPaths.gpgAgentConf)
             ?.content[GPG_AGENT_PINENTRY_PROGRAM_CONF_KEY] == gpgAgentPaths.gpgPinentryAppLauncherConfigPath
  }

  fun configure() {
    cs.launch { withContext(Dispatchers.IO) { doConfigure() } }
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
    if (!isEnabled(project, executable)) return

    val gpgAgentPaths = pathLocator?.resolvePaths() ?: createPathLocator(executable).resolvePaths() ?: return
    val gpgAgentConf = gpgAgentPaths.gpgAgentConf
    var needBackup = gpgAgentConf.exists()
    if (!needBackup) {
      LOG.debug("Cannot locate $gpgAgentConf, creating new")
      writeAgentConfig(gpgAgentPaths, GpgAgentConfig(gpgAgentConf, emptyMap()))
      restartAgent(executable)
      needBackup = false
    }

    val config = readConfig(gpgAgentConf)
    if (config == null) {
      LOG.debug("Empty $gpgAgentConf, skipping pinentry program configuration")
      return
    }

    if (needBackup && backupExistingConfig(gpgAgentPaths, config)) {
      writeAgentConfig(gpgAgentPaths, config)
      restartAgent(executable)
    }

    generatePinentryLauncher(executable, gpgAgentPaths)
  }

  private fun readConfig(gpgAgentConf: Path): GpgAgentConfig? {
    val config = mutableMapOf<String, String>()
    try {
      for (line in gpgAgentConf.readLines()) {
        val keyValue = line.split(' ')
        val key: String
        val value: String
        when (keyValue.size) {
          1 -> {
            key = keyValue[0]; value = ""
          }
          2 -> {
            key = keyValue[0]; value = keyValue[1]
          }
          else -> continue
        }
        config[key] = value
      }
    }
    catch (e: IOException) {
      LOG.error("Cannot read $gpgAgentConf", e)
      return null
    }
    return GpgAgentConfig(gpgAgentConf, config)
  }

  private fun emitUpdateLauncherEvent() {
    updateLauncherQueue.queue(Update.create(GPG_AGENT_PINENTRY_PROGRAM_CONF_KEY) {
      updateExistingPinentryLauncher()
    })
  }

  private fun updateExistingPinentryLauncher() {
    val executable = GitExecutableManager.getInstance().getExecutable(project)
    if (isEnabled(project, executable) && isConfigured(project)) {
      val gpgAgentPaths = createPathLocator(executable).resolvePaths()

      if (gpgAgentPaths != null) {
        //always regenerate the launcher to be up to date (e.g., java.home could be changed between versions)
        generatePinentryLauncher(executable, gpgAgentPaths)
      }
    }
  }

  @Synchronized
  private fun generatePinentryLauncher(executable: GitExecutable, gpgAgentPaths: GpgAgentPaths) {
    val gpgAgentConfBackup = gpgAgentPaths.gpgAgentConfBackup
    val pinentryFallback = when {
      gpgAgentConfBackup.exists() -> readConfig(gpgAgentConfBackup)?.content[GPG_AGENT_PINENTRY_PROGRAM_CONF_KEY]
      else -> null
    }
    if (pinentryFallback.isNullOrBlank()) {
      LOG.warn("Pinentry fallback not found in $gpgAgentConfBackup. " +
               "Some features of GPG (as a key manipulation) may not work correctly without default pinentry-program specified in this file.")
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

  private fun writeAgentConfig(gpgAgentPaths: GpgAgentPaths, config: GpgAgentConfig) {
    val pinentryAppLauncherConfigPath = gpgAgentPaths.gpgPinentryAppLauncherConfigPath
    val (configPath, configContent) = config
    val configToSave = configContent.toMutableMap()
    configToSave.put(GPG_AGENT_PINENTRY_PROGRAM_CONF_KEY, pinentryAppLauncherConfigPath)
    val notificator = project.service<GpgAgentConfigurationNotificator>()
    try {
      FileUtil.writeToFile(configPath.toFile(),
                           configToSave.map { (key, value) -> "$key $value".trimEnd() }.joinToString(separator = "\n"))

      notificator.notifyConfigurationSuccessful(gpgAgentPaths)
    }
    catch (e: IOException) {
      LOG.warn("Cannot change config $configPath", e)
      notificator.notifyConfigurationFailed(e)
      deleteBackup(gpgAgentPaths)
    }
  }

  private fun deleteBackup(paths: GpgAgentPaths) {
    val gpgAgentConfBackup = paths.gpgAgentConfBackup
    try {
      Files.deleteIfExists(gpgAgentConfBackup)
    }
    catch (e: Exception) {
      LOG.warn("Cannot delete config $gpgAgentConfBackup", e)
    }
  }

  private fun restartAgent(executor: GitExecutable) {
    try {
      val output = createGpgAgentExecutor(executor).execute("gpg-connect-agent", "reloadagent", "/bye")
      if (output.contains("OK")) {
        LOG.debug("Gpg Agent restarted successfully")
      }
      else {
        LOG.warn("Gpg Agent restart failed, restart manually to apply config changes $output")
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
    val processOutput = CapturingProcessHandler
      .Silent(GeneralCommandLine(command).withParameters(*params))
      .runProcess(10000, true)
    return processOutput.stdoutLines + processOutput.stderrLines
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
    val processOutput = CapturingProcessHandler
      .Silent(commandLine)
      .runProcess(10000, true)
    return processOutput.stdoutLines + processOutput.stderrLines
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
              |  case "${'$'}$PINENTRY_USER_DATA_ENV" in
              |    ${PinentryData.PREFIX}*)
              |      ${addParameters(*getCommandLineParameters()).commandLine(PinentryApp::class.java, false)}
              |      exit $?
              |    ;;
              |  esac
              |fi
              |exec $fallbackPinentryPath "$@"
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
    ProjectLevelVcsManager.getInstance(project).runAfterInitialization {
      project.service<GpgAgentConfigurator>().init()
      project.service<GpgAgentConfigurationNotificator>().proposeCustomPinentryAgentConfiguration(isSuggestion = true)
    }
  }
}
