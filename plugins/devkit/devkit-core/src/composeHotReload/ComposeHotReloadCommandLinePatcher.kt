// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.composeHotReload

import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.application.ApplicationConfiguration
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.download.DownloadableFileService
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import org.jetbrains.idea.devkit.DevKitBundle
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.*

private val logger = logger<ComposeHotReloadCommandLinePatcher>()

private const val COMPOSE_HOT_RELOAD_AGENT_DEFAULT_VERSION = "1.0.0-beta04"
private const val COMPOSE_HOT_RELOAD_AGENT_FILE_PREFIX = "compose-hot-reload-agent"
private const val HOT_RELOAD_ENABLED_REGISTRY_KEY = "devkit.compose.hot.reload.enabled"
private const val HOT_RELOAD_AGENT_VERSION_REGISTRY_KEY = "devkit.compose.hot.reload.agent.version"

private val COMPOSE_HOT_RELOAD_AGENT_VERSION: String get() {
  val versionFromRegistry = try {
    Registry.stringValue(HOT_RELOAD_AGENT_VERSION_REGISTRY_KEY)
  }
  catch (_: MissingResourceException) {
    ""
  }
  return versionFromRegistry.ifEmpty { COMPOSE_HOT_RELOAD_AGENT_DEFAULT_VERSION }
}
                                                //https://repo1.maven.org/maven2/org/jetbrains/compose/hot-reload/hot-reload-agent/1.0.0-beta04/hot-reload-agent-1.0.0-beta04-standalone.jar
private val COMPOSE_HOT_RELOAD_AGENT_URL get() = "https://repo1.maven.org/maven2/org/jetbrains/compose/hot-reload/hot-reload-agent/$COMPOSE_HOT_RELOAD_AGENT_VERSION/hot-reload-agent-$COMPOSE_HOT_RELOAD_AGENT_VERSION-standalone.jar"
private val COMPOSE_HOT_RELOAD_AGENT_FILE_NAME get() = "$COMPOSE_HOT_RELOAD_AGENT_FILE_PREFIX-$COMPOSE_HOT_RELOAD_AGENT_VERSION.jar"

class ComposeHotReloadCommandLinePatcher : RunConfigurationExtension() {

  class ComposeHotReloadRegistryListener : RegistryValueListener {
    override fun afterValueChanged(value: RegistryValue) {
      if (value.key == HOT_RELOAD_ENABLED_REGISTRY_KEY && value.asBoolean()) {
        val project = IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project ?: ProjectManager.getInstance().openProjects.firstOrNull()
        service<ComposeHotReloadAgentHolder>().triggerDownload(project)
      }
    }
  }

  @Service(Service.Level.APP)
  private class ComposeHotReloadAgentHolder(val cs: CoroutineScope) {
    private val downloadJob: AtomicReference<Job?> = AtomicReference(null)
    val agentFilePath: Path get() = Path(PathManager.getTempPath(), COMPOSE_HOT_RELOAD_AGENT_FILE_NAME)

    fun triggerDownload(project: Project?) {
      val currentAgentFilePath = agentFilePath
      if (!currentAgentFilePath.exists()) {
        logger.info("Compose Hot Reload agent not found at '$currentAgentFilePath'. Downloading it from '$COMPOSE_HOT_RELOAD_AGENT_URL'...")

        val job = cs.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
          try {
            downloadAgent(project)
          }
          catch (t: Throwable) {
            logger.error("Failed to download Compose Hot Reload agent", t)
            Notification("Debugger", DevKitBundle.message("compose.hot.reload.failed.to.download.compose.hot.reload.agent"), NotificationType.ERROR).notify(project)
          }
          finally {
            downloadJob.compareAndSet(coroutineContext.job, null)
          }
        }
        val prevValue = downloadJob.compareAndExchange(null, job)
        if (prevValue == null) {
          job.start()
          logger.info("Compose Hot Reload agent download job has been started")
        }
        else {
          job.cancel()
          logger.info("Compose Hot Reload agent download already in progress")
        }
      }
      else {
        logger.trace { "Compose Hot Reload agent already downloaded to '$currentAgentFilePath'" }
      }
    }

    private suspend fun downloadAgent(project: Project?) {
      val downloadingNotification = Notification("Debugger", DevKitBundle.message("compose.hot.reload.restart.your.run.configuration", agentFilePath), NotificationType.INFORMATION)
      downloadingNotification.notify(project)

      val tempFile = Files.createTempFile(Path(PathManager.getTempPath()), COMPOSE_HOT_RELOAD_AGENT_FILE_NAME, ".tmp").apply {
        // we need only the name
        deleteIfExists()
      }

      val fileService = DownloadableFileService.getInstance()
      val fileDescription = fileService.createFileDescription(COMPOSE_HOT_RELOAD_AGENT_URL, tempFile.fileName.pathString)
      val downloader = fileService.createDownloader(listOf(fileDescription), "Compose Hot Reload Agent")
      val downloadedFilesFuture = downloader.downloadWithBackgroundProgress(PathManager.getTempPath(), project)
      cleanOldAgents()
      val agentTempFile = downloadedFilesFuture.await()?.firstOrNull() ?: throw IllegalStateException("Failed to download Compose Hot Reload agent")
      val agentTempFilePath = Path(agentTempFile.first.path)
      try {
        agentTempFilePath.moveTo(agentFilePath, overwrite = true)
      }
      catch (_: Throwable) {
        agentTempFilePath.deleteIfExists()
        throw IllegalStateException("Failed to move Compose Hot Reload agent from '$agentTempFilePath' to '$agentFilePath'")
      }
      downloadingNotification.expire()
      Notification("Debugger", DevKitBundle.message("compose.hot.reload.hot.reload.agent.has.been.saved", agentFilePath), NotificationType.INFORMATION).notify(project)
    }
  }

  override fun <T : RunConfigurationBase<*>?> updateJavaParameters(configuration: T & Any, params: JavaParameters, runnerSettings: RunnerSettings?) {
    val project = configuration.project
    val agentHolder = service<ComposeHotReloadAgentHolder>()

    val agentFilePath = agentHolder.agentFilePath
    if (!agentFilePath.exists()) {
      agentHolder.triggerDownload(project)
      return
    }

    val vmParametersList = params.vmParametersList
    vmParametersList.add("-javaagent:${agentFilePath.pathString}")
    vmParametersList.add("-Dcompose.reload.devToolsEnabled=false")
    vmParametersList.add("-XX:+AllowEnhancedClassRedefinition")
    vmParametersList.add("-Didea.dev.additional.classpath=${agentFilePath.pathString}")
  }

  override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
    return Registry.`is`(HOT_RELOAD_ENABLED_REGISTRY_KEY, false) && (configuration is ApplicationConfiguration //use this instead of 'is KotlinRunConfiguration' to avoid having dependency on Kotlin plugin here
                                                                     || configuration.factory?.id == "JetRunConfigurationType")
  }
}

private fun cleanOldAgents() {
  try {
    for (it in Path(PathManager.getTempPath()).listDirectoryEntries("$COMPOSE_HOT_RELOAD_AGENT_FILE_PREFIX-*.jar")) {
      if (it.fileName.toString() == COMPOSE_HOT_RELOAD_AGENT_FILE_NAME) continue // skip the actual version
      try {
        it.toFile().delete()
      }
      catch (_: Throwable) {
        // ignore
      }
    }
  }
  catch (_ : Throwable) {
    // ignore
  }
}