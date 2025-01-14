// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit.signing

import com.intellij.CommonBundle
import com.intellij.execution.CommandLineUtil
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.util.PathUtilRt
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
import git4idea.i18n.GitBundle
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
import kotlin.Throws
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.io.path.readLines

private val LOG = logger<GpgAgentConfigurator>()

@Service(Service.Level.PROJECT)
internal class GpgAgentConfigurator(private val project: Project, private val cs: CoroutineScope): Disposable {
  companion object {
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
  fun canBeConfigured(project: Project): Boolean {
    val executable = GitExecutableManager.getInstance().getExecutable(project)
    if (!isEnabled(project, executable)) return false
    val gpgAgentPaths = createPathLocator(executable).resolvePaths() ?: return false
    val config = readConfig(gpgAgentPaths.gpgAgentConf).getOrThrow() ?: return true

    return !isPinentryConfigured(gpgAgentPaths, config)
  }

  private fun isPinentryConfigured(gpgAgentPaths: GpgAgentPaths, config: GpgAgentConfig): Boolean {
    return gpgAgentPaths.gpgPinentryAppLauncher.exists() && config.isIntellijPinentryConfigured(gpgAgentPaths)
  }

  fun configure() {
    cs.launch {
      val executable = GitExecutableManager.getInstance().getExecutable(project)
      if (!isEnabled(project, executable)) return@launch
      val gpgAgentPaths = createPathLocator(executable).resolvePaths() ?: TODO()
      val defaultPinentry = readDefaultPinentryPathFromGpgConf(executable) ?: TODO()
      checkCanceled()
      if (showConfirmationDialog(defaultPinentry)) {
        withContext(Dispatchers.IO) { doConfigure(executable, gpgAgentPaths, defaultPinentry) }
      }
    }
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
  internal fun doConfigure(gitExecutable: GitExecutable, gpgAgentPaths: GpgAgentPaths, pinentryFallback: String) {
    val gpgAgentConfPath = gpgAgentPaths.gpgAgentConf
    val existingConfig = readConfig(gpgAgentConfPath).getOrThrow()
    if (existingConfig == null) {
      LOG.info("Cannot locate $gpgAgentConfPath, creating new")
      writeAgentConfig(gpgAgentPaths, GpgAgentConfig(gpgAgentPaths, pinentryFallback))
      restartAgent(gitExecutable)
    } else {
      val launcherConfigured = existingConfig.isIntellijPinentryConfigured(gpgAgentPaths)
      if (launcherConfigured) {
        LOG.info("Existing GPG config already has pinentry launcher configured")
      } else {
        backupExistingConfig(gpgAgentPaths)
        writeAgentConfig(gpgAgentPaths, existingConfig.copyAndSetDefaults(gpgAgentPaths.gpgPinentryAppLauncherConfigPath))
        restartAgent(gitExecutable)
      }
    }

    generatePinentryLauncher(gitExecutable, gpgAgentPaths, pinentryFallback)
  }

  private fun readConfig(gpgAgentConf: Path): Result<GpgAgentConfig?> {
    if (!gpgAgentConf.exists()) {
      LOG.debug("Cannot find $gpgAgentConf")
      return Result.success(null)
    }
    val config = mutableMapOf<String, String>()
    return runCatching {
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
      GpgAgentConfig(gpgAgentConf, config)
    }
  }

  private fun emitUpdateLauncherEvent() {
    updateLauncherQueue.queue(Update.create("pinentry") {
      updateExistingPinentryLauncher()
    })
  }

  private fun updateExistingPinentryLauncher() {
    val executable = GitExecutableManager.getInstance().getExecutable(project)
    if (isEnabled(project, executable)) {
      val gpgAgentPaths = createPathLocator(executable).resolvePaths() ?: return
      val config = readConfig(gpgAgentPaths.gpgAgentConf).getOrThrow() ?: return
      if (isPinentryConfigured(gpgAgentPaths, config)) {
        // There might be some previously created configurations before pinentry-program-fallback was introduced.
        // In this case, let's try to get explicitly defined pinentry program from the backup config.
        val pinentryFallback = config.pinentryProgramFallback ?:
          readConfig(gpgAgentPaths.gpgAgentConfBackup).getOrThrow()?.pinentryProgram

        //always regenerate the launcher to be up to date (e.g., java.home could be changed between versions)
        generatePinentryLauncher(executable, gpgAgentPaths, pinentryFallback)
      }
    }
  }

  @Synchronized
  private fun generatePinentryLauncher(
    executable: GitExecutable,
    gpgAgentPaths: GpgAgentPaths,
    pinentryFallback: String?,
  ) {
    LOG.info("Creating pinentry launcher with fallback: ${pinentryFallback ?: "-"}")
    PinentryShellScriptLauncherGenerator(executable).generate(project, gpgAgentPaths, pinentryFallback)
  }

  @Throws(IOException::class)
  private fun backupExistingConfig(gpgAgentPaths: GpgAgentPaths) {
    val gpgAgentConf = gpgAgentPaths.gpgAgentConf
    val gpgAgentConfBackup = gpgAgentPaths.gpgAgentConfBackup
    try {
      LOG.info("Creating config backup in $gpgAgentConfBackup")
      gpgAgentConf.copyTo(gpgAgentConfBackup, overwrite = true)
    }
    catch (e: IOException) {
      LOG.warn("Cannot backup config $gpgAgentConf to $gpgAgentConfBackup", e)
      throw e
    }
  }

  private fun writeAgentConfig(gpgAgentPaths: GpgAgentPaths, config: GpgAgentConfig) {
    val notificator = project.service<GpgAgentConfigurationNotificator>()
    try {
      config.writeToFile()
      notificator.notifyConfigurationSuccessful(gpgAgentPaths)
    }
    catch (e: IOException) {
      LOG.warn("Cannot change config ${config.path}", e)
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
        LOG.info("Gpg Agent restarted successfully")
      }
      else {
        LOG.warn("Gpg Agent restart failed, restart manually to apply config changes $output")
      }
    }
    catch (e: ExecutionException) {
      LOG.warn("Gpg Agent restart failed, restart manually to apply config changes", e)
    }
  }

  private suspend fun showConfirmationDialog(defaultPinentry: String): Boolean = withContext(Dispatchers.EDT) {
    val productName = ApplicationNamesInfo.getInstance().fullProductName
    MessageDialogBuilder
      .yesNo(
        title = GitBundle.message("gpg.pinentry.agent.configuration.confirmation.title"),
        message = GitBundle.message("gpg.pinentry.agent.configuration.confirmation.text", productName, defaultPinentry)
      )
      .noText(CommonBundle.getCancelButtonText())
      .yesText(GitBundle.message("gpg.pinentry.agent.configuration.confirmation.yes"))
      .help(GitBundle.message("gpg.jb.manual.link"))
      .ask(project)
  }

  @NlsSafe
  private suspend fun readDefaultPinentryPathFromGpgConf(executable: GitExecutable): String? = try {
    val output = withContext(Dispatchers.IO) {
      createGpgAgentExecutor(executable).execute("gpgconf", "--list-components")
    }

    val pinentryLine = output.find { it.startsWith("pinentry:") }
    //pinentry:Passphrase Entry:<pinentry-path>
    val defaultPinentry = pinentryLine?.split(":")?.getOrNull(2)
    if (defaultPinentry == null) {
      LOG.warn("Failed to detect default pinentry path from: $output")
    }

    defaultPinentry
  }
  catch (e: ExecutionException) {
    LOG.warn("Failed to get default pinentry from `gpgconf`", e)
    null
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
      val gpgPinentryAppLauncher = gpgAgentHome.resolve(PINENTRY_LAUNCHER_FILE_NAME)

      return GpgAgentPaths(gpgAgentHome, gpgPinentryAppLauncher.toAbsolutePath().toString())
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
      val wslUserHome = getPathInWslUserHome(executable) ?: return null
      val pathToPinentryAppInWsl = "$wslUserHome/$GPG_HOME_DIR/$PINENTRY_LAUNCHER_FILE_NAME"
      return GpgAgentPaths(gpgAgentHome, pathToPinentryAppInWsl)
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
              |exec ${CommandLineUtil.posixQuote(fallbackPinentryPath)} "$@"
           """.trimMargin()
  }
}

internal data class GpgAgentPaths(
  val gpgAgentHome: Path,
  val gpgPinentryAppLauncherConfigPath: String,
) {
  val gpgAgentConf = gpgAgentHome.resolve(GPG_AGENT_CONF_FILE_NAME)
  val gpgAgentConfBackup = gpgAgentHome.resolve(GPG_AGENT_CONF_BACKUP_FILE_NAME)
  val gpgPinentryAppLauncher = gpgAgentHome.resolve(PINENTRY_LAUNCHER_FILE_NAME)
}

internal data class GpgAgentConfig(val path: Path, val content: Map<String, String>) {
  val pinentryProgram: String? get() = content[PINENTRY_PROGRAM]
  val pinentryProgramFallback: String? get() = content[PINENTRY_PROGRAM_FALLBACK]

  constructor(gpgAgentPaths: GpgAgentPaths, pinentryFallback: String) :
    this(gpgAgentPaths.gpgAgentConf, buildMap {
      setDefaults(this)
      this[PINENTRY_PROGRAM] = gpgAgentPaths.gpgPinentryAppLauncherConfigPath
      this[PINENTRY_PROGRAM_FALLBACK] = pinentryFallback
    })

  fun isIntellijPinentryConfigured(paths: GpgAgentPaths): Boolean =
    pinentryProgram == paths.gpgPinentryAppLauncherConfigPath

  fun copyAndSetDefaults(pinentryPath: String): GpgAgentConfig {
    val contentCopy = content.toMutableMap()
    setDefaults(contentCopy)
    contentCopy[PINENTRY_PROGRAM] = pinentryPath
    return GpgAgentConfig(path, contentCopy)
  }

  @Throws(IOException::class)
  fun writeToFile() {
    LOG.info("Writing gpg agent config to ${path.toFile()}")
    FileUtil.writeToFile(path.toFile(),
                         content.map { (key, value) -> "$key $value".trimEnd() }.joinToString(separator = "\n"))
  }

  companion object {
    const val PINENTRY_PROGRAM = "pinentry-program"
    const val PINENTRY_PROGRAM_FALLBACK = "pinentry-program-fallback"
    const val DEFAULT_CACHE_TTL = "default-cache-ttl"
    const val MAX_CACHE_TTL = "max-cache-ttl"

    const val DEFAULT_CACHE_TTL_CONF_VALUE = "1800"
    const val MAX_CACHE_TTL_CONF_VALUE = "7200"

    private fun setDefaults(content: MutableMap<String, String>) {
      content[DEFAULT_CACHE_TTL] = DEFAULT_CACHE_TTL_CONF_VALUE
      content[MAX_CACHE_TTL] = MAX_CACHE_TTL_CONF_VALUE
    }
  }
}

private class GpgAgentConfiguratorStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    ProjectLevelVcsManager.getInstance(project).runAfterInitialization {
      project.service<GpgAgentConfigurator>().init()
      project.service<GpgAgentConfigurationNotificator>().proposeCustomPinentryAgentConfiguration(isSuggestion = true)
    }
  }
}
