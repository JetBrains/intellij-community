// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.plugins.IdeaPluginDependency
import com.intellij.ide.plugins.PluginManagementPolicy
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.contentModuleId
import com.intellij.ide.ui.OptionsSearchTopHitProvider
import com.intellij.ide.ui.search.BooleanOptionDescription
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PluginAutoUpdateRepository
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportProgress
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import com.intellij.util.io.move
import com.intellij.util.messages.Topic
import com.intellij.util.text.VersionComparatorUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.name

/**
 * It is expected that plugin updates are consumed at the startup.
 *
 * Currently, updates are pushed here from UpdateChecker and related code
 */
@Service(Service.Level.APP)
internal class PluginAutoUpdateService(private val cs: CoroutineScope) {
  private val updatesState: MutableMap<PluginId, DownloadedUpdate> = ConcurrentHashMap()
  private val pendingDownloads: Channel<List<PluginDownloader>> = Channel(capacity = Channel.UNLIMITED)
  private var downloadManagerJob: Job? = null

  fun isAutoUpdateEnabled(): Boolean = Companion.isAutoUpdateEnabled()

  private fun setupDownloadManager() {
    synchronized(this) {
      if (isAutoUpdateEnabled()) {
        if (downloadManagerJob?.isActive != true) {
          LOG.debug { "setting up download manager" }
          downloadManagerJob = cs.launchDownloadManager()
        }
      } else {
        val job = downloadManagerJob
        job?.cancel()
        while (true) { // drain pending downloads
          pendingDownloads.tryReceive().getOrNull() ?: break
        }
        cs.launch(Dispatchers.IO) { // TODO this coroutine might race with next downloadManagerJob
          job?.join()
          dropDownloadedUpdates()
        }
      }
    }
  }

  private fun CoroutineScope.launchDownloadManager(): Job {
    return launch(CoroutineName("Download manager")) {
      for (downloaders in pendingDownloads) {
        if (!isAutoUpdateEnabled()) {
          currentCoroutineContext().cancel()
        }
        ensureActive()
        LOG.debug { "new plugin updates: ${downloaders.joinToString { it.pluginName }}" }
        if (downloaders.isEmpty()) {
          continue
        }
        val activeProject = ProjectUtil.getActiveProject()
        val downloadedList = if (activeProject != null) {
          withBackgroundProgress(activeProject, IdeBundle.message("update.downloading.plugins.progress"), true) {
            LOG.debug { "downloading with background progress in project $activeProject" }
            downloadUpdates(downloaders)
          }
        } else {
          LOG.debug { "downloading without background progress" }
          downloadUpdates(downloaders)
        }
        if (downloadedList.isNotEmpty()) {
          LOG.debug { "adding downloaded updates to the repository: ${downloadedList.joinToString { it.pluginName }}" }
          withContext(Dispatchers.IO) {
            PluginAutoUpdateRepository.addUpdates(updatesState.mapValues {
              PluginAutoUpdateRepository.PluginUpdateInfo(
                pluginPath = PluginManagerCore.getPlugin(it.key)!!.pluginPath.absolutePathString(),
                updateFilename = it.value.updatePath.name
              )
            })
          }
          notifyUpdatesDownloaded(downloadedList)
        }
      }
    }
  }

  private suspend fun downloadUpdates(downloaders: List<PluginDownloader>): List<PluginDownloader> {
    val downloadedList = mutableListOf<PluginDownloader>()
    val enabledPluginsAndModules: Set<String> = PluginManagerCore.getPluginSet().getEnabledModules().flatMap {
      listOf(it.contentModuleId ?: it.pluginId.idString) + it.pluginAliases.map { id -> id.idString }
    }.toSet()
    val downloaders = downloaders.filter { downloader ->
      val existingUpdateState = updatesState[downloader.id]
      if (PluginManagerCore.getPlugin(downloader.id) == null) {
        LOG.debug { "skipping the update for plugin ${downloader.pluginName}, as the plugin is not installed" }
        return@filter false
      }
      if (existingUpdateState != null && VersionComparatorUtil.compare(existingUpdateState.version, downloader.pluginVersion) >= 0) {
        LOG.debug { "skipping the update for plugin ${downloader.pluginName}, since it is already downloaded" }
        return@filter false
      }
      val unsatisfiedDependencies = findUnsatisfiedDependencies(
        updateDescriptor = downloader.toPluginNode().dependencies,
        enabledPluginsAndModulesIds = enabledPluginsAndModules,
      )
      if (unsatisfiedDependencies.isNotEmpty()) {
        LOG.debug {
          "skipping the update for plugin ${downloader.pluginName}, since dependencies won't be satisfied: " +
          unsatisfiedDependencies.joinToString { it.pluginId.toString() }
        }
        return@filter false
      }
      return@filter true
    }
    reportProgress(downloaders.size) { reporter ->
      for (downloader in downloaders) {
        currentCoroutineContext().ensureActive()
        if (!isAutoUpdateEnabled()) {
          throw CancellationException("auto-update disabled")
        }
        reporter.itemStep(IdeBundle.message("progress.downloading.plugin", downloader.pluginName)) {
          LOG.debug { "downloading ${downloader.pluginName}" }
          val plugin = PluginManagerCore.getPlugin(downloader.id)
                       ?: return@itemStep
          if (!plugin.isBundled) {
            downloader.setOldFile(plugin.pluginPath)
          }
          val updatePathInAutoUpdateDir = withContext(Dispatchers.IO) {
            val updateFile = coroutineToIndicator {
              downloader.tryDownloadPlugin(ProgressManager.getInstanceOrNull()?.progressIndicator)
            }
            val autoUpdateDir = PluginAutoUpdateRepository.getAutoUpdateDirPath()
            val updatePathInAutoUpdatesDir = autoUpdateDir.resolve(updateFile.fileName)
            if (!autoUpdateDir.exists()) {
              autoUpdateDir.createDirectories()
            }
            if (updatePathInAutoUpdatesDir.exists()) {
              LOG.warn("update for plugin ${downloader.id} located in file ${updateFile.fileName} already exists and will be overwritten")
              updatePathInAutoUpdatesDir.delete()
            }
            ensureActive()
            updateFile.move(updatePathInAutoUpdatesDir)
            updatePathInAutoUpdatesDir
          }
          updatesState[downloader.id] = DownloadedUpdate(downloader.id, downloader.pluginVersion, updatePathInAutoUpdateDir)
          downloadedList.add(downloader)
        }
      }
    }
    return downloadedList
  }

  private fun notifyUpdatesDownloaded(downloaded: List<PluginDownloader>) {
    LOG.info("updates for plugins ${downloaded.joinToString(", ") { it.pluginName }} were downloaded " +
             "(${updatesState.size} updates are prepared in total)")
  }

  fun onPluginUpdatesChecked(updates: List<PluginDownloader>) {
    LOG.debug { "onPluginUpdateCheck: ${updates.joinToString { it.pluginName }}" }
    val sent = pendingDownloads.trySend(updates)
    if (sent.isFailure) {
      LOG.error("failed to schedule updates for downloading")
    }
    setupDownloadManager()
  }

  internal fun onSettingsChanged() {
    LOG.debug { "onSettingsChanged: " +
                "allowed=${PluginManagementPolicy.getInstance().isPluginAutoUpdateAllowed()} " +
                "enabled=${UpdateSettings.getInstance().isPluginsAutoUpdateEnabled} " }
    // should erase already downloaded updates if the setting gets disabled
    // TODO this thing is not bullet-proof, only explicit setting change from the UI is tracked
    cs.launch {
      setupDownloadManager()
    }

    ApplicationManager.getApplication().messageBus.syncPublisher(PluginAutoUpdateListener.TOPIC).settingsChanged()
  }

  private fun dropDownloadedUpdates() {
    updatesState.clear()
    if (PluginAutoUpdateRepository.getAutoUpdateDirPath().exists()) {
      LOG.info("plugin auto-update repository is deleted because auto-update is disabled")
      try {
        PluginAutoUpdateRepository.clearUpdates()
      } catch (e: Exception) {
        LOG.error(e)
      }
    }
  }

  internal class PluginAutoUpdateAppLifecycleListener : AppLifecycleListener {
    override fun appWillBeClosed(isRestart: Boolean) {
      if (!isAutoUpdateEnabled()) {
        serviceIfCreated<PluginAutoUpdateService>()?.dropDownloadedUpdates()
      }
    }
  }


  private companion object {
    fun isAutoUpdateEnabled(): Boolean = PluginManagementPolicy.getInstance().isPluginAutoUpdateAllowed() &&
                                         UpdateSettings.getInstance().isPluginsAutoUpdateEnabled
  }
}

internal interface PluginAutoUpdateListener {
  fun settingsChanged()

  companion object {
    @Topic.AppLevel
    val TOPIC: Topic<PluginAutoUpdateListener> = Topic<PluginAutoUpdateListener>(PluginAutoUpdateListener::class.java, Topic.BroadcastDirection.TO_DIRECT_CHILDREN)
  }
}

private data class DownloadedUpdate(@JvmField val pluginId: PluginId, @JvmField val version: String, @JvmField val updatePath: Path)

private val LOG
  get() = logger<PluginAutoUpdateService>()

// TODO such functionality must be extracted into a single place com.intellij.ide.plugins.PluginInstaller.findNotInstalledPluginDependencies
//          com.intellij.ide.plugins.PluginInstallOperation.checkMissingDependencies
/**
 * @returns a list of unmet dependencies
 */
@ApiStatus.Internal
fun findUnsatisfiedDependencies(
  updateDescriptor: Collection<IdeaPluginDependency>,
  enabledPluginsAndModulesIds: Collection<String>,
): List<IdeaPluginDependency> {
  return updateDescriptor.filter { dep ->
    if (dep.isOptional) {
      return@filter false
    }
    val dependencySatisfied = enabledPluginsAndModulesIds.any { it == dep.pluginId.idString }
    !dependencySatisfied
  }
}

internal class PluginAutoUpdateOptionsProvider : OptionsSearchTopHitProvider.ApplicationLevelProvider {
  override fun getId() = "PluginAutoUpdate"

  override fun getOptions(): List<BooleanOptionDescription> {
    if (!PluginManagementPolicy.getInstance().isPluginAutoUpdateAllowed()) {
      return emptyList()
    }
    return listOf(object : BooleanOptionDescription(IdeBundle.message("updates.plugins.autoupdate.se.option"), null) {
      override fun isOptionEnabled() = UpdateSettings.getInstance().isPluginsAutoUpdateEnabled

      override fun setOptionState(enabled: Boolean) {
        UpdateSettings.getInstance().isPluginsAutoUpdateEnabled = enabled
        service<PluginAutoUpdateService>().onSettingsChanged()
      }
    })
  }
}