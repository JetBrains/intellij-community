// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.PluginAutoUpdateRepository
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import com.intellij.util.io.move
import com.intellij.util.text.VersionComparatorUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
  private var downloadManagerScope: CoroutineScope? = null

  fun isAutoUpdateEnabled(): Boolean = UpdateSettings.getInstance().isPluginsAutoUpdateEnabled

  private fun setupDownloadManager() {
    synchronized(this) {
      if (isAutoUpdateEnabled()) {
        if (downloadManagerScope == null || downloadManagerScope?.isActive != true) {
          val managerScope = cs.childScope("Download manager")
          managerScope.launch { downloadManager() }
          downloadManagerScope = managerScope
        }
      } else {
        downloadManagerScope?.cancel()
        while (true) { // drain pending downloads
          pendingDownloads.tryReceive().getOrNull() ?: break
        }
      }
    }
  }

  private suspend fun CoroutineScope.downloadManager() {
    for (downloaders in pendingDownloads) {
      ensureActive()
      // download updates one by one for now, TODO
      val downloadedList = mutableListOf<PluginDownloader>()
      for (downloader in downloaders) {
        ensureActive()
        if (!isAutoUpdateEnabled()) {
          throw CancellationException("auto-update disabled")
        }
        val existingUpdateState = updatesState[downloader.id]
        if (existingUpdateState != null && VersionComparatorUtil.compare(existingUpdateState.version, downloader.pluginVersion) >= 0) {
          continue
        }
        val plugin = PluginManagerCore.getPlugin(downloader.id)
                     ?: continue
        if (!plugin.isBundled) downloader.setOldFile(plugin.pluginPath)
        val updatePathInAutoUpdateDir = withContext(Dispatchers.IO) {
          val updateFile = coroutineToIndicator {
            downloader.tryDownloadPlugin(ProgressManager.getGlobalProgressIndicator())
          }
          val updatePath = updateFile.toPath()
          val autoUpdateDir = PluginAutoUpdateRepository.getAutoUpdateDirPath()
          val updatePathInAutoUpdatesDir = autoUpdateDir.resolve(updatePath.fileName)
          if (!autoUpdateDir.exists()) {
            autoUpdateDir.createDirectories()
          }
          if (updatePathInAutoUpdatesDir.exists()) {
            LOG.warn("update for plugin ${downloader.id} located in file ${updatePath.fileName} already exists and will be overwritten")
            updatePathInAutoUpdatesDir.delete()
          }
          ensureActive()
          updatePath.move(updatePathInAutoUpdatesDir)
          updatePathInAutoUpdatesDir
        }
        updatesState[downloader.id] = DownloadedUpdate(downloader.id, downloader.pluginVersion, updatePathInAutoUpdateDir)
        downloadedList.add(downloader)
      }
      if (downloadedList.isNotEmpty()) {
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

  private fun notifyUpdatesDownloaded(downloaded: List<PluginDownloader>) {
    LOG.info("updates for plugins ${downloaded.joinToString(", ") { it.pluginName }} were downloaded " +
             "(${updatesState.size} updates are prepared in total)")
  }

  fun onPluginUpdatesCheck(updates: List<PluginDownloader>) {
    val sent = pendingDownloads.trySend(updates)
    if (sent.isFailure) {
      LOG.error("failed to schedule updates for downloading")
    }
    setupDownloadManager()
  }

  private data class DownloadedUpdate(val pluginId: PluginId, val version: String, val updatePath: Path)
}

private val LOG get() = logger<PluginAutoUpdateService>()
