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
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.APP)
@State(name = "PluginUpdateExclusionService", storages = [Storage("pluginUpdateExclusions.xml", roamingType = RoamingType.DISABLED)])
@ApiStatus.Internal
class PluginUpdateExclusionService :
  SimplePersistentStateComponent<PluginUpdateExclusionState>(PluginUpdateExclusionState()) {

  enum class Exclusion {
    FromAutoUpdate,
    FromUpdate,
  }

  companion object {
    @JvmStatic
    fun getInstance(): PluginUpdateExclusionService = service()
  }

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
    if (exclusion == null) {
      state.excludedPlugins.remove(pluginId.idString)
    }
    else {
      state.excludedPlugins[pluginId.idString] = exclusion
    }
  }
}

@ApiStatus.Internal
class PluginUpdateExclusionState : BaseState() {
  @get:XMap(propertyElementName = "plugins", entryTagName = "plugin", keyAttributeName = "id")
  val excludedPlugins: MutableMap<String, PluginUpdateExclusionService.Exclusion> by map()
}
