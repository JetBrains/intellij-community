// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit.signing

import com.intellij.CommonBundle
import com.intellij.execution.CommandLineUtil
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.idea.AppMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.openapi.vcs.VcsException
import com.intellij.platform.util.coroutines.flow.debounceBatch
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import git4idea.commands.GitScriptGenerator
import git4idea.commit.signing.GpgAgentPathsLocator.Companion.GPG_AGENT_CONF_BACKUP_FILE_NAME
import git4idea.commit.signing.GpgAgentPathsLocator.Companion.GPG_AGENT_CONF_FILE_NAME
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<GpgAgentConfigurator>()

@Service(Service.Level.PROJECT)
internal class GpgAgentConfigurator(private val project: Project, private val cs: CoroutineScope): Disposable {
  private val configurationLock = Mutex()

  companion object {
    @JvmStatic
    fun isEnabled(project: Project, executable: GitExecutable): Boolean =
      (Registry.`is`("git.commit.gpg.signing.enable.embedded.pinentry", false) || application.isUnitTestMode)
      && (SystemInfo.isUnix || executable is GitExecutable.Wsl)
      && signingIsEnabledInAnyRepo(project)

    private fun isUnitTestModeOnUnix(): Boolean =
      SystemInfo.isUnix && application.isUnitTestMode

    private fun isRemDevOrWsl(executable: GitExecutable): Boolean =
      AppMode.isRemoteDevHost() || executable is GitExecutable.Wsl

    // do not configure Gpg Agent for roots without commit.gpgSign and user.signingkey enabled
    private fun signingIsEnabledInAnyRepo(project: Project): Boolean = GitRepositoryManager.getInstance(project)
      .repositories.any { repository ->
        isGpgSignEnabledCached(repository)
        && getGpgSignKeyCached(repository) != null
      }

    @JvmStatic
    fun getInstance(project: Project): GpgAgentConfigurator = project.service()
  }

  private val updateLauncherFlow = Channel<Unit>(1, BufferOverflow.DROP_OLDEST)

  fun init() {
    cs.launch {
      updateLauncherFlow.consumeAsFlow().debounceBatch(100.milliseconds).collect {
        updateExistingPinentryLauncher()
      }
    }

    application.messageBus.connect(this).subscribe(GitExecutableManager.TOPIC, GitExecutableListener {
      project.service<GpgAgentConfigurationNotificator>().proposeCustomPinentryAgentConfiguration(isSuggestion = true)
      updateLauncherFlow.trySend(Unit)
    })
    project.messageBus.connect(this).subscribe(GitConfigListener.TOPIC, object: GitConfigListener {
      override fun notifyConfigChanged(repository: GitRepository) {
        project.service<GpgAgentConfigurationNotificator>().proposeCustomPinentryAgentConfiguration(isSuggestion = true)
        updateLauncherFlow.trySend(Unit)
      }
    })

    updateLauncherFlow.trySend(Unit)
  }

  @RequiresBackgroundThread
  fun canBeConfigured(project: Project): Boolean {
    val executable = GitExecutableManager.getInstance().getExecutable(project)
    // Additional condition extending isEnabled check.
    // We want to show the configuration notification in Remote Development mode or in WSL only.
    // However, we have to preserve compatibility (updating launcher and enabling PinentryService) for those who
    // have already configured pinentry for non-remdev Unix
    if (!isRemDevOrWsl(executable) && !isUnitTestModeOnUnix()) return false

    if (!isEnabled(project, executable)) return false
    val gpgAgentPaths = resolveGpgAgentPaths(executable) ?: return false
    val config = GpgAgentConfig.readConfig(gpgAgentPaths.gpgAgentConf) ?: return true

    return !isPinentryConfigured(gpgAgentPaths, config)
  }

  @RequiresBackgroundThread
  fun isConfigured(): Boolean {
    val executable = GitExecutableManager.getInstance().getExecutable(project)
    val gpgAgentPaths = resolveGpgAgentPaths(executable) ?: return false
    val config = GpgAgentConfig.readConfig(gpgAgentPaths.gpgAgentConf) ?: return false

    return isPinentryConfigured(gpgAgentPaths, config)
  }

  private fun isPinentryConfigured(gpgAgentPaths: GpgAgentPaths, config: GpgAgentConfig): Boolean {
    return gpgAgentPaths.gpgPinentryAppLauncher.exists() && config.isIntellijPinentryConfigured(gpgAgentPaths)
  }

  fun configure() {
    cs.launch {
      configurationLock.withLock {
        val executable = GitExecutableManager.getInstance().getExecutable(project)
        if (!isEnabled(project, executable)) return@launch
        try {
          val gpgAgentPaths = resolveGpgAgentPaths(executable) ?: throw ReadGpgAgentConfigException(null)
          val existingConfig = readConfig(gpgAgentPaths.gpgAgentConf)
          val defaultPinentry = existingConfig?.pinentryProgram ?: readDefaultPinentryPathFromGpgConf(executable)
          if (defaultPinentry == gpgAgentPaths.gpgPinentryAppLauncherConfigPath) {
            LOG.warn("GPG Agent config already has pinentry launcher configured. Skipping")
            return@launch
          }
          checkCanceled()
          if (showConfirmationDialog(defaultPinentry)) {
            doConfigure(executable, gpgAgentPaths, existingConfig, defaultPinentry)
          }
        }
        catch (e: VcsException) {
          project.service<GpgAgentConfigurationNotificator>().notifyConfigurationFailed(GitBundle.message("gpg.pinentry.agent.configuration.exception", e.message))
        }
      }
    }
  }

  private fun createGpgAgentExecutor(executor: GitExecutable): GpgAgentCommandExecutor {
    if (executor is GitExecutable.Wsl) {
      return WslGpgAgentCommandExecutor(project, executor)
    }
    return LocalGpgAgentCommandExecutor()
  }

  @VisibleForTesting
  internal suspend fun doConfigure(
    gitExecutable: GitExecutable,
    gpgAgentPaths: GpgAgentPaths,
    existingConfig: GpgAgentConfig?,
    pinentryFallback: String,
  ) {
    val gpgAgentConfPath = gpgAgentPaths.gpgAgentConf
    generatePinentryLauncher(gitExecutable, gpgAgentPaths, pinentryFallback)
    var backupCreated = false
    if (existingConfig == null) {
      LOG.info("Cannot locate $gpgAgentConfPath, creating new")
      writeAgentConfig(GpgAgentConfig(gpgAgentPaths))
      restartAgent(gitExecutable)
    }
    else {
      backupExistingConfig(gpgAgentPaths)
      backupCreated = true
      writeAgentConfig(GpgAgentConfig(gpgAgentPaths, existingConfig))
      restartAgent(gitExecutable)
    }
    project.service<GpgAgentConfigurationNotificator>().notifyConfigurationSuccessful(gpgAgentPaths, backupCreated)
  }

  @VisibleForTesting
  internal suspend fun updateExistingPinentryLauncher() = configurationLock.withLock {
    val executable = GitExecutableManager.getInstance().getExecutable(project)
    if (isEnabled(project, executable)) {
      val gpgAgentPaths = resolveGpgAgentPaths(executable) ?: return
      val config = readConfig(gpgAgentPaths.gpgAgentConf) ?: return
      if (isPinentryConfigured(gpgAgentPaths, config)) {
        val pinentryFallback = readConfig(gpgAgentPaths.gpgAgentConfBackup)?.pinentryProgram
          ?: readDefaultPinentryPathFromGpgConf(executable)

        //always regenerate the launcher to be up to date (e.g., java.home could be changed between versions)
        generatePinentryLauncher(executable, gpgAgentPaths, pinentryFallback)
      }
    }
  }

  private suspend fun generatePinentryLauncher(executable: GitExecutable, gpgAgentPaths: GpgAgentPaths, pinentryFallback: String?) {
    LOG.info("Creating pinentry launcher with fallback: ${pinentryFallback ?: "-"}")
    PinentryShellScriptLauncherGenerator(executable).generate(project, gpgAgentPaths, pinentryFallback)
  }

  private suspend fun readConfig(path: Path): GpgAgentConfig? = withContext(Dispatchers.IO) {
    GpgAgentConfig.readConfig(path)
  }

  private fun resolveGpgAgentPaths(executable: GitExecutable): GpgAgentPaths? =
    application.service<GpgAgentPathsLocatorFactory>().createPathLocator(project, executable).resolvePaths()

  private suspend fun backupExistingConfig(gpgAgentPaths: GpgAgentPaths) {
    val gpgAgentConf = gpgAgentPaths.gpgAgentConf
    val gpgAgentConfBackup = gpgAgentPaths.gpgAgentConfBackup
    try {
      LOG.info("Creating config backup in $gpgAgentConfBackup")
      withContext(Dispatchers.IO) {
        gpgAgentConf.copyTo(gpgAgentConfBackup, overwrite = true)
      }
    }
    catch (e: IOException) {
      LOG.warn("Cannot backup config $gpgAgentConf to $gpgAgentConfBackup", e)
      throw BackupGpgAgentConfigException(e)
    }
  }

  private suspend fun writeAgentConfig(config: GpgAgentConfig) = withContext(Dispatchers.IO) {
    try {
      config.writeToFile()
    }
    catch (e: IOException) {
      LOG.warn("Cannot change config ${config.path}", e)
      throw SaveGpgAgentConfigException(e)
    }
  }

  private suspend fun restartAgent(executor: GitExecutable) {
    try {
      val output = withContext(Dispatchers.IO) {
        createGpgAgentExecutor(executor).execute("gpg-connect-agent", "reloadagent", "/bye")
      }
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
  private suspend fun readDefaultPinentryPathFromGpgConf(executable: GitExecutable): String = try {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw AssertionError("Should not be called in testing code, as returned value is system-configuration dependent")
    }

    val output = withContext(Dispatchers.IO) {
      createGpgAgentExecutor(executable).execute("gpgconf", "--list-components")
    }

    val pinentryLine = output.find { it.startsWith("pinentry:") }
    //pinentry:Passphrase Entry:<pinentry-path>
    val defaultPinentry = pinentryLine?.split(":")?.getOrNull(2)
    if (defaultPinentry == null) {
      LOG.warn("Failed to detect default pinentry path from: $output")
      throw GpgPinentryProgramDetectionException(null)
    }

    defaultPinentry
  }
  catch (e: ExecutionException) {
    LOG.warn("Failed to get default pinentry from `gpgconf`", e)
    throw GpgPinentryProgramDetectionException(e)
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

internal interface PinentryLauncherGenerator {
  val executable: GitExecutable
  fun getScriptTemplate(fallbackPinentryPath: String?): String

  suspend fun generate(project: Project, gpgAgentPaths: GpgAgentPaths, fallbackPinentryPath: String?) = withContext(Dispatchers.IO) {
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
      throw GenerateLauncherException(e)
    }
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
  val gpgAgentConf: Path,
  val gpgAgentConfBackup: Path,
  val gpgPinentryAppLauncher: Path,
) {
  companion object {
    @Throws(InvalidPathException::class)
    fun create(gpgAgentHome: Path, gpgPinentryAppLauncherConfigPath: String) = GpgAgentPaths(
      gpgAgentHome = gpgAgentHome,
      gpgPinentryAppLauncherConfigPath = gpgPinentryAppLauncherConfigPath,
      gpgAgentConf = gpgAgentHome.resolve(GPG_AGENT_CONF_FILE_NAME),
      gpgAgentConfBackup = gpgAgentHome.resolve(GPG_AGENT_CONF_BACKUP_FILE_NAME),
      gpgPinentryAppLauncher = gpgAgentHome.resolve(PINENTRY_LAUNCHER_FILE_NAME),
    )
  }
}

private class GpgAgentConfiguratorStartupActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    ProjectLevelVcsManager.getInstance(project).runAfterInitialization {
      GpgAgentConfigurator.getInstance(project).init()
      project.service<GpgAgentConfigurationNotificator>().proposeCustomPinentryAgentConfiguration(isSuggestion = true)
    }
  }
}
