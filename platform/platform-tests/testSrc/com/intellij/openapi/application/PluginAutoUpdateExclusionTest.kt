// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.application.PluginAutoUpdateRepository.PluginUpdateInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.PluginAutoUpdateService
import com.intellij.openapi.updateSettings.impl.PluginUpdateExclusionService
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class PluginAutoUpdateExclusionTest : UpdateCheckerTestBase() {
  @Test
  fun `plugin excluded from auto-update is removed from prepared updates`() = timeoutRunBlocking {
    val excludedPlugin = "ColourChooser"
    val retainedPlugin = "ImageView"
    val excludedPluginId = PluginId.getId(excludedPlugin)
    val retainedPluginId = PluginId.getId(retainedPlugin)
    val autoUpdateService = service<PluginAutoUpdateService>()
    val updatesState = getAutoUpdateState(autoUpdateService)
    val exclusionService = PluginUpdateExclusionService.getInstance()

    PluginAutoUpdateRepository.clearUpdates()
    updatesState.clear()
    updatesState[excludedPluginId] = Any()
    updatesState[retainedPluginId] = Any()
    PluginAutoUpdateRepository.addUpdates(mapOf(
      excludedPluginId to createPluginUpdateInfo(excludedPlugin),
      retainedPluginId to createPluginUpdateInfo(retainedPlugin),
    ))

    try {
      exclusionService.setExcluded(excludedPluginId, PluginUpdateExclusionService.Exclusion.FromAutoUpdate)

      waitUntil("Prepared auto-update state must drop the excluded plugin", 2.seconds) {
        updatesState.keys == setOf(retainedPluginId) &&
        PluginAutoUpdateRepository.readUpdates().keys == setOf(retainedPluginId)
      }
    }
    finally {
      exclusionService.setExcluded(excludedPluginId, null)
      updatesState.clear()
      PluginAutoUpdateRepository.clearUpdates()
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun getAutoUpdateState(service: PluginAutoUpdateService): MutableMap<PluginId, Any> {
    val stateField = PluginAutoUpdateService::class.java.getDeclaredField("updatesState")
    stateField.isAccessible = true
    return stateField.get(service) as MutableMap<PluginId, Any>
  }

  private fun createPluginUpdateInfo(pluginId: String): PluginUpdateInfo {
    return PluginUpdateInfo(PluginAutoUpdateRepository.getAutoUpdateDirPath().resolve(pluginId).toString(),
                            "$pluginId.zip")
  }
}
