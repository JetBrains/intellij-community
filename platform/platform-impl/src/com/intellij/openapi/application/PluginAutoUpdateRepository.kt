// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import kotlinx.coroutines.Deferred
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.isReadable
import kotlin.io.path.isRegularFile

@ApiStatus.Internal
object PluginAutoUpdateRepository {
  const val PLUGIN_AUTO_UPDATE_DIRECTORY_NAME: String = "plugins-auto-update"
  private const val STATE_FILE_NAME: String = ".autoupdate.data"

  fun getAutoUpdateDirPath(): Path = PathManager.getStartupScriptDir().resolve(PLUGIN_AUTO_UPDATE_DIRECTORY_NAME)

  private fun getAutoUpdateStatePath(): Path = getAutoUpdateDirPath().resolve(STATE_FILE_NAME)

  @Synchronized
  private fun clearState() {
    if (getAutoUpdateStatePath().isRegularFile()) {
      getAutoUpdateStatePath().deleteExisting()
    }
  }

  @Synchronized
  fun clearUpdates() {
    if (getAutoUpdateDirPath().exists()) {
      getAutoUpdateDirPath().delete(recursively = true)
    }
  }

  @Synchronized
  fun readUpdates(): Map<PluginId, PluginUpdateInfo> {
    val stateFile = getAutoUpdateStatePath()
    if (!stateFile.isReadable()) {
      return Collections.emptyMap<PluginId, PluginUpdateInfo>()
    }
    val updatesRaw = Files.readString(stateFile)
    return json.decodeFromString<PluginUpdatesData>(updatesRaw).updates.mapKeys { PluginId.getId(it.key) }
  }

  @Synchronized
  private fun writeUpdates(updatesData: PluginUpdatesData) {
    val dir = getAutoUpdateDirPath()
    if (!dir.exists()) {
      dir.createDirectories()
    }
    val updatesRaw = json.encodeToString(PluginUpdatesData.serializer(), updatesData)
    Files.writeString(getAutoUpdateStatePath(), updatesRaw)
  }

  @Synchronized
  fun addUpdates(updates: Map<PluginId, PluginUpdateInfo>) {
    val existing = readUpdates()
    val result: Map<PluginId, PluginUpdateInfo> = existing + updates
    writeUpdates(PluginUpdatesData(result.mapKeys { it.key.idString }))
  }

  @Synchronized
  fun filterUpdates(predicate: (PluginId, PluginUpdateInfo) -> Boolean): Boolean {
    val existing = readUpdates()
    val result = existing.filter { (pluginId, updateInfo) -> predicate(pluginId, updateInfo) }
    if (result.size == existing.size) {
      return false
    }
    if (result.isEmpty()) {
      clearState()
    }
    else {
      writeUpdates(PluginUpdatesData(result.mapKeys { it.key.idString }))
    }
    return true
  }

  suspend fun safeConsumeUpdates(logDeferred: Deferred<Logger>): Map<PluginId, PluginUpdateInfo> {
    val (readResult, clearResult) = synchronized(this) {
      runCatching { readUpdates() } to runCatching { clearState() }
    }
    val updates = readResult.getOrHandleException { logDeferred.await().warn("Failed to read updates", it) } ?: emptyMap()
    clearResult.getOrHandleException { logDeferred.await().warn("Failed to clear plugin auto update state", it) }
    return updates
  }

  /**
   * @param updates key is the plugin id
   */
  @Serializable
  data class PluginUpdatesData(val updates: Map<String, PluginUpdateInfo>)

  /**
   * @param pluginPath absolute path to the plugin that will be updated (it may be both bundled and non-bundled plugin)
   * @param updateFilename plugin update location in the plugins-auto-update directory
   */
  @Serializable
  data class PluginUpdateInfo(val pluginPath: String, val updateFilename: String)

  private val json = Json { ignoreUnknownKeys = true }
}
