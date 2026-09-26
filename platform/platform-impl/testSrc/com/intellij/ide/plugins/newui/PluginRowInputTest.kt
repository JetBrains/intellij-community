// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.ListPluginModel
import com.intellij.ide.plugins.api.PluginDto
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceService
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class PluginRowInputTest {
  @Test
  fun `list model data is replaced as one snapshot`() {
    val oldId = PluginId.getId("old.plugin")
    val newId = PluginId.getId("new.plugin")
    val oldPlugin = PluginDto("Old", oldId)
    val newPlugin = PluginDto("New", newId)
    val oldState = PluginInstallationState(false, null)
    val newState = PluginInstallationState(true, null)
    val oldUpdateSource = PluginUpdateSourceService.getInstance().createCustomRepositoryPluginUpdateSourceId("old.example.com")
    val newUpdateSource = PluginUpdateSourceService.getInstance().createCustomRepositoryPluginUpdateSourceId("new.example.com")
    val model = ListPluginModel(
      installedModels = mutableMapOf(oldId to oldPlugin),
      errors = mutableMapOf(oldId to listOf(HtmlChunk.text("old"))),
      pluginInstallationStates = mutableMapOf(oldId to oldState),
      updateSources = mutableMapOf(oldId to oldUpdateSource),
    )

    model.replaceAll(
      installedModels = mapOf(newId to newPlugin),
      errors = mapOf(newId to listOf(HtmlChunk.text("new"))),
      installationStates = mapOf(newId to newState),
      updateSources = mapOf(newId to newUpdateSource),
    )

    assertThat(model.installedModels).containsExactlyEntriesOf(mapOf(newId to newPlugin))
    assertThat(model.errors.keys).containsExactly(newId)
    assertThat(model.pluginInstallationStates).containsExactlyEntriesOf(mapOf(newId to newState))
    assertThat(model.updateSources).containsExactlyEntriesOf(mapOf(newId to newUpdateSource))
  }
}
