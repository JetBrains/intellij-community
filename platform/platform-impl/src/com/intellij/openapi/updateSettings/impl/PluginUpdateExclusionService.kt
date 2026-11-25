// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.xmlb.annotations.XMap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

internal const val PLUGIN_UPDATE_EXCLUSIONS_FILE_NAME = "pluginUpdateExclusions.xml"

@Service(Service.Level.APP)
@State(name = "PluginUpdateExclusionService",
       storages = [Storage(PLUGIN_UPDATE_EXCLUSIONS_FILE_NAME)],
       category = SettingsCategory.PLUGINS)
@ApiStatus.Internal
class PluginUpdateExclusionService :
  SimplePersistentStateComponent<PluginUpdateExclusionState>(PluginUpdateExclusionState()) {

  @Serializable
  enum class Exclusion {
    FromAutoUpdate,
    FromUpdate,
  }

  companion object {
    @JvmStatic
    fun getInstance(): PluginUpdateExclusionService = service()

    @JvmStatic
    fun isFunctionalitySupported(): Boolean {
      return PluginUpdateExclusionEarlyStartupReader.isFunctionalitySupportedEarlyCheck() &&
             Registry.`is`("platform.enable.plugin.update.mode.feature", false)
    }
  }

  val updateExclusionChanges: Flow<Unit>
    field = MutableSharedFlow<Unit>(
      extraBufferCapacity = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

  fun getExclusion(pluginId: PluginId): Exclusion? {
    return if (!isFunctionalitySupported()) null else state.excludedPlugins[pluginId.idString]
  }

  fun allowedUpdate(pluginId: PluginId): Boolean {
    return if (!isFunctionalitySupported()) true else getExclusion(pluginId) != Exclusion.FromUpdate
  }

  fun allowedAutoupdate(pluginId: PluginId): Boolean {
    return if (!isFunctionalitySupported()) true else getExclusion(pluginId) == null
  }

  fun setExcluded(pluginId: PluginId, exclusion: Exclusion?) {
    if (!isFunctionalitySupported()) return
    val oldValue = state.excludedPlugins[pluginId.idString]
    if (oldValue == exclusion) return
    if (exclusion == null) {
      state.excludedPlugins.remove(pluginId.idString)
    }
    else {
      state.excludedPlugins[pluginId.idString] = exclusion
    }
    notifyStateUpdated()
  }

  override fun loadState(state: PluginUpdateExclusionState) {
    super.loadState(state)
    notifyStateUpdated()
  }

  private fun notifyStateUpdated() {
    if (!isFunctionalitySupported()) return
    updateExclusionChanges.tryEmit(Unit)
  }
}

@ApiStatus.Internal
class PluginUpdateExclusionState : BaseState() {
  @get:XMap(propertyElementName = "plugins", entryTagName = "plugin", keyAttributeName = "id")
  val excludedPlugins: MutableMap<String, PluginUpdateExclusionService.Exclusion> by map()
}
