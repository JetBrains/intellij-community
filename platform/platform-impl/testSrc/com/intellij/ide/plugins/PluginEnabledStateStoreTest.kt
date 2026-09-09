// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.marketplace.InitSessionResult
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class PluginEnabledStateStoreTest {
  @Test
  fun `enabled state snapshots stay stable after publication`() {
    val first = PluginId.getId("first")
    val second = PluginId.getId("second")
    val store = PluginEnabledStateStore()
    store.putAll(mapOf(first to PluginEnabledState.ENABLED, second to PluginEnabledState.DISABLED))
    val initialSnapshot = store.snapshot()

    store.putAll(mapOf(first to PluginEnabledState.DISABLED, second to PluginEnabledState.ENABLED))

    assertThat(initialSnapshot).containsExactlyInAnyOrderEntriesOf(
      mapOf(first to PluginEnabledState.ENABLED, second to PluginEnabledState.DISABLED)
    )
    assertThat(store.snapshot()).containsExactlyInAnyOrderEntriesOf(
      mapOf(first to PluginEnabledState.DISABLED, second to PluginEnabledState.ENABLED)
    )
  }

  @Test
  fun `bulk state change publishes one complete generation`() {
    val first = PluginId.getId("first")
    val second = PluginId.getId("second")
    val ids = setOf(first, second)
    val expected = ids.associateWith { PluginEnabledState.DISABLED }
    val model = RecordingInstalledPluginsTableModel()

    model.setStates(ids, enabled = false)

    assertThat(model.snapshots).hasSize(ids.size)
    model.snapshots.forEach { snapshot ->
      assertThat(snapshot).containsExactlyInAnyOrderEntriesOf(expected)
    }
  }

  private class RecordingInstalledPluginsTableModel : InstalledPluginsTableModel(
    project = null,
    initSessionResult = InitSessionResult(),
  ) {
    val snapshots = mutableListOf<Map<PluginId, PluginEnabledState?>>()

    fun setStates(ids: Set<PluginId>, enabled: Boolean) {
      setStatesByIds(ids, enabled)
    }

    override fun setEnabled(pluginId: PluginId, enabled: PluginEnabledState?) {
      snapshots.add(enabledStateSnapshot())
      super.setEnabled(pluginId, enabled)
    }
  }
}
