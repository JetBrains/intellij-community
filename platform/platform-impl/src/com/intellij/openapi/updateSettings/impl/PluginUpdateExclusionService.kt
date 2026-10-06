// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.xmlb.annotations.XMap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.APP)
@State(name = "PluginUpdateExclusionService", storages = [Storage("pluginUpdateExclusions.xml", roamingType = RoamingType.DISABLED)])
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
  }

  val updateExclusionChanges: Flow<Unit>
    field = MutableSharedFlow<Unit>(
      extraBufferCapacity = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

  fun getExclusion(pluginId: PluginId): Exclusion? {
    return state.excludedPlugins[pluginId.idString]
  }

  fun allowedUpdate(pluginId: PluginId): Boolean {
    return getExclusion(pluginId) != Exclusion.FromUpdate
  }

  fun allowedAutoupdate(pluginId: PluginId): Boolean {
    return getExclusion(pluginId) == null
  }

  fun setExcluded(pluginId: PluginId, exclusion: Exclusion?) {
    val oldValue = state.excludedPlugins[pluginId.idString]
    if (oldValue == exclusion) return
    if (exclusion == null) {
      state.excludedPlugins.remove(pluginId.idString)
    }
    else {
      state.excludedPlugins[pluginId.idString] = exclusion
    }
    updateExclusionChanges.tryEmit(Unit)
  }

  override fun loadState(state: PluginUpdateExclusionState) {
    super.loadState(state)
    notifyStateUpdated(updateExclusionChanges)
  }

  private fun notifyStateUpdated(updateExclusionChanges: MutableSharedFlow<Unit>) {
    updateExclusionChanges.tryEmit(Unit)
  }
}

@ApiStatus.Internal
class PluginUpdateExclusionState : BaseState() {
  @get:XMap(propertyElementName = "plugins", entryTagName = "plugin", keyAttributeName = "id")
  val excludedPlugins: MutableMap<String, PluginUpdateExclusionService.Exclusion> by map()
}
