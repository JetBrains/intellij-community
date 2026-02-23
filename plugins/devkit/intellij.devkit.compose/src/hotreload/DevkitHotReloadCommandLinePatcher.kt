// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.compose.hotreload

import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.devkit.compose.DevkitComposeBundle
import com.intellij.devkit.compose.hasCompose
import com.intellij.devkit.compose.icons.DevkitComposeIcons
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.JavaRunConfigurationBase
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunnableState
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemTaskDebugRunner
import com.intellij.openapi.project.IntelliJProjectUtil.isIntelliJPlatformProject
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsActions.ActionDescription
import com.intellij.openapi.util.NlsActions.ActionText
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.util.download.DownloadableFileService
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.idea.devkit.util.PsiUtil.isPluginProject
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionContext
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelperExtension
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString

private const val COMPOSE_HOT_RELOAD_AGENT_DEFAULT_VERSION = "1.1.0-alpha03"
private const val COMPOSE_HOT_RELOAD_AGENT_FILE_PREFIX = "agent"
private const val COMPOSE_HOT_RELOAD_GRADLE_ARG = "--compose-hot-reload"

private val LOG = logger<DevkitHotReloadCommandLinePatcher>()

private val COMPOSE_HOT_RELOAD_AGENT_VERSION: String
  get() {
    val versionFromRegistry = Registry.stringValue("devkit.compose.hot.reload.agent.version")
    return versionFromRegistry.ifEmpty { COMPOSE_HOT_RELOAD_AGENT_DEFAULT_VERSION }
  }

private fun getAgentTempPath(): Path {
  return PathManager.getTempDir().resolve("devkit-hot-reload")
}

private val COMPOSE_HOT_RELOAD_AGENT_URL: String
  get() = "https://repo1.maven.org/maven2/org/jetbrains/compose/hot-reload/hot-reload-agent/$COMPOSE_HOT_RELOAD_AGENT_VERSION/hot-reload-agent-$COMPOSE_HOT_RELOAD_AGENT_VERSION-standalone.jar"
private val COMPOSE_HOT_RELOAD_AGENT_FILE_NAME: String
  get() = "$COMPOSE_HOT_RELOAD_AGENT_FILE_PREFIX-$COMPOSE_HOT_RELOAD_AGENT_VERSION.jar"
private val agentFilePath: Path
  get() = getAgentTempPath().resolve(COMPOSE_HOT_RELOAD_AGENT_FILE_NAME)

private const val DEVKIT_HOT_RELOAD_EXECUTOR_ID = "DevkitComposeHotReloadExecutor"
private const val DEVKIT_HOT_RELOAD_RUNNER_ID = "DevkitHotReloadRunner"
private const val DEVKIT_GRADLE_HOT_RELOAD_RUNNER_ID = "DevkitGradleHotReloadRunner"
private val DEVKIT_HOT_RELOAD_EXECUTOR_ID_KEY = Key.create<Boolean>(DEVKIT_HOT_RELOAD_EXECUTOR_ID)

private fun isRelevantContext(project: Project): Boolean {
  return isIntelliJPlatformProject(project) || isPluginProject(project) && hasCompose(project)
}

internal class DevkitHotReloadExecutor : Executor() {
  override fun getId(): @NonNls String = DEVKIT_HOT_RELOAD_EXECUTOR_ID
  override fun getContextActionId(): @NonNls String = "DebugComposeHotReload"
  override fun getToolWindowId(): String = ToolWindowId.DEBUG

  override fun getToolWindowIcon(): Icon = AllIcons.Toolwindows.ToolWindowDebugger
  override fun getIcon(): Icon = DevkitComposeIcons.ComposeHotReload
  override fun getDisabledIcon(): Icon? = null

  @Suppress("DialogTitleCapitalization")
  override fun getDescription(): @ActionDescription String = DevkitComposeBundle.message("action.DevkitComposeHotReloadExecutorAction.description")
  override fun getActionName(): @ActionText String = DevkitComposeBundle.message("action.DevkitComposeHotReloadExecutorAction.text")

  override fun getStartActionText(): @Nls(capitalization = Nls.Capitalization.Title) String {
    return DevkitComposeBundle.message("action.DevkitComposeHotReloadExecutorAction.text")
  }

  override fun getStartActionText(configurationName: @NlsSafe String): @Nls(capitalization = Nls.Capitalization.Title) String {
    if (configurationName.isEmpty()) return startActionText
    val configName = shortenNameIfNeeded(configurationName)
    return DevkitComposeBundle.message("action.DevkitComposeHotReloadExecutorAction.text.0", configName)
  }

  override fun getHelpId(): @NonNls String? = null

  override fun isApplicable(project: Project): Boolean {
    return isRelevantContext(project)
  }
}

internal class DevkitHotReloadRunner : GenericDebuggerRunner() {
  override fun getRunnerId(): String = DEVKIT_HOT_RELOAD_RUNNER_ID

  override fun canRun(executorId: String, profile: RunProfile): Boolean {
    return executorId == DEVKIT_HOT_RELOAD_EXECUTOR_ID
           && profile !is ExternalSystemRunConfiguration
           && profile is ApplicationConfiguration
           && isRelevantContext(profile.configurationModule.project)
  }

  @Throws(ExecutionException::class)
  override fun execute(environment: ExecutionEnvironment) {
    ensureAgentDownloaded(environment.project)

    super.execute(environment)
  }

  @Throws(ExecutionException::class)
  internal fun ensureAgentDownloaded(project: Project) {
    val currentAgentFilePath = agentFilePath
    if (!currentAgentFilePath.exists()) {
      LOG.info("Compose Hot Reload agent not found at '$currentAgentFilePath'. Downloading it from '$COMPOSE_HOT_RELOAD_AGENT_URL'...")

      try {
        downloadAgentFile(project)
      }
      catch (t: Throwable) {
        throw ExecutionException(DevkitComposeBundle.message("compose.hot.reload.failed.to.download.compose.hot.reload.agent"), t)
      }
    }
    else {
      LOG.debug("Compose Hot Reload agent already downloaded to '$currentAgentFilePath'")
    }
  }

  private fun downloadAgentFile(project: Project): Path? {
    val fileService = DownloadableFileService.getInstance()
    val fileDescription = fileService.createFileDescription(COMPOSE_HOT_RELOAD_AGENT_URL, COMPOSE_HOT_RELOAD_AGENT_FILE_NAME)
    val downloader = fileService.createDownloader(listOf(fileDescription), "Compose Hot Reload Agent")

    Files.createDirectories(getAgentTempPath())
    downloader.downloadFilesWithProgress(getAgentTempPath().absolutePathString(), project, null)

    cleanOldAgents()

    return getAgentTempPath().resolve(COMPOSE_HOT_RELOAD_AGENT_FILE_NAME)
      .takeIf { it.exists() }
  }

  private fun cleanOldAgents() {
    val agentTempPath = getAgentTempPath()
    if (!agentTempPath.exists()) return

    try {
      for (it in agentTempPath.listDirectoryEntries("$COMPOSE_HOT_RELOAD_AGENT_FILE_PREFIX-*.jar")) {
        if (it.fileName.toString() == COMPOSE_HOT_RELOAD_AGENT_FILE_NAME) continue // skip the actual version

        try {
          Files.delete(it)
        }
        catch (t: Throwable) {
          // ignore
          LOG.warn("Failed to delete old Compose Hot Reload agent file", t)
        }
      }
    }
    catch (_: Throwable) {
      // ignore
    }
  }
}

// applied for Gradle-based plugins
internal class DevkitGradleHotReloadRunner : ExternalSystemTaskDebugRunner() {
  override fun getRunnerId(): String = DEVKIT_GRADLE_HOT_RELOAD_RUNNER_ID

  override fun canRun(executorId: String, profile: RunProfile): Boolean {
    return executorId == DEVKIT_HOT_RELOAD_EXECUTOR_ID
           && profile is ExternalSystemRunConfiguration
           && runReadActionBlocking { isRelevantContext(profile.project) }
  }

  override fun doExecute(state: RunProfileState, env: ExecutionEnvironment): RunContentDescriptor? {
    if (state is ExternalSystemRunnableState) {
      state.putUserData(DEVKIT_HOT_RELOAD_EXECUTOR_ID_KEY, true)
    }
    return super.doExecute(state, env)
  }
}

internal class DevkitGradleExecutionHelper : GradleExecutionHelperExtension {
  override fun configureSettings(settings: GradleExecutionSettings, context: GradleExecutionContext) {
    if (settings.getUserData(DEVKIT_HOT_RELOAD_EXECUTOR_ID_KEY) == true) {
      settings.withArgument(COMPOSE_HOT_RELOAD_GRADLE_ARG)
    }

    super.configureSettings(settings, context)
  }
}

// applied for IntelliJ IDEs development
internal class DevkitHotReloadCommandLinePatcher : RunConfigurationExtension() {

  override fun <T : RunConfigurationBase<*>?> updateJavaParameters(
    configuration: T & Any,
    params: JavaParameters,
    runnerSettings: RunnerSettings?,
  ) {
    // only update parameters for our executor, here we don't know it
  }

  override fun <T : RunConfigurationBase<*>?> updateJavaParameters(
    configuration: T & Any,
    params: JavaParameters,
    runnerSettings: RunnerSettings?,
    executor: Executor,
  ) {
    if (executor.id != DEVKIT_HOT_RELOAD_EXECUTOR_ID) return
    if (!isIntelliJPlatformProject(configuration.project)) return
    if (configuration !is JavaRunConfigurationBase) return

    if (params.mainClass != "org.jetbrains.intellij.build.devServer.DevMainKt"
        && params.mainClass != "com.intellij.idea.Main"
        && params.mainClass != "com.android.tools.idea.Main") {
      // only IDE build configurations supported here so far
      return
    }

    val module = configuration.configurationModule.module ?: return
    val jdk = JavaParameters.getJdkToRunModule(module, true) ?: return
    val versionString = jdk.versionString ?: ""
    if (!versionString.contains("JetBrains Runtime")) {
      // we may only expect -XX:+AllowEnhancedClassRedefinition on JBR
      return
    }

    val vmParametersList = params.vmParametersList
    vmParametersList.add("-javaagent:${agentFilePath.pathString}")
    vmParametersList.add("-Dcompose.reload.devToolsEnabled=false")
    vmParametersList.add("-Dcompose.reload.staticsReinitializeMode=AllDirty")
    vmParametersList.add("-XX:+AllowEnhancedClassRedefinition")
    vmParametersList.add("-Didea.dev.additional.classpath=${agentFilePath.pathString}")
  }

  override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
    //use this instead of 'is KotlinRunConfiguration' to avoid having dependency on Kotlin plugin here
    return configuration is ApplicationConfiguration
           || configuration.factory?.id == "JetRunConfigurationType"
  }
}