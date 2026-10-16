// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.openapi.extensions.PluginId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

internal class DefaultPluginUpdatesProviderTest {
  @Test
  fun `disabling retains known updates for later session enablement`() {
    val disabled = PluginDto("Disabled Plugin", PluginId.getId("disabled.plugin"))
    val enabled = PluginDto("Enabled Plugin", PluginId.getId("enabled.plugin"))
    val previouslyDisabled = PluginDto("Previously Disabled Plugin", PluginId.getId("previously.disabled.plugin"))
    val custom = PluginDto("Custom Plugin", PluginId.getId("custom.plugin"))
    val updates = PluginUpdatesEvent(listOf(disabled, enabled), listOf(previouslyDisabled), listOf(custom))

    val result = updates.withDisabledPlugins(setOf(disabled.pluginId))

    assertThat(result.enabledUpdates).containsExactly(enabled)
    assertThat(result.disabledUpdates).containsExactly(previouslyDisabled, disabled)
    assertThat(result.all).containsExactlyInAnyOrder(disabled, enabled, previouslyDisabled)
    assertThat(result.pluginNods).containsExactly(custom)
    assertThat(result.withDisabledPlugins(setOf(disabled.pluginId))).isSameAs(result)
  }

  @TestFactory
  fun `disabling merges sources for cached updates with the same plugin id`(): List<DynamicTest> {
    val sources = listOf(
      Triple(PluginSource.LOCAL, PluginSource.REMOTE, PluginSource.BOTH),
      Triple(PluginSource.REMOTE, PluginSource.LOCAL, PluginSource.BOTH),
      Triple(PluginSource.LOCAL, PluginSource.BOTH, PluginSource.BOTH),
      Triple(PluginSource.BOTH, PluginSource.LOCAL, PluginSource.BOTH),
      Triple(PluginSource.REMOTE, PluginSource.BOTH, PluginSource.BOTH),
      Triple(PluginSource.BOTH, PluginSource.REMOTE, PluginSource.BOTH),
      Triple(PluginSource.BOTH, PluginSource.BOTH, PluginSource.BOTH),
      Triple(PluginSource.LOCAL, PluginSource.LOCAL, PluginSource.LOCAL),
      Triple(PluginSource.REMOTE, PluginSource.REMOTE, PluginSource.REMOTE),
    )
    return sources.map { (disabledSource, enabledSource, expectedSource) ->
      dynamicTest("$disabledSource + $enabledSource") {
        val pluginId = PluginId.getId("split.plugin")
        val disabled = PluginDto("Split Plugin", pluginId).apply { source = disabledSource }
        val enabled = PluginDto("Split Plugin", pluginId).apply { source = enabledSource }
        val updates = PluginUpdatesEvent(listOf(enabled), listOf(disabled), emptyList())

        val result = updates.withDisabledPlugins(setOf(pluginId))

        assertThat(result.enabledUpdates).isEmpty()
        assertThat(result.disabledUpdates).hasSize(1)
        assertThat(result.disabledUpdates.single().pluginId).isEqualTo(pluginId)
        assertThat(result.disabledUpdates.single().source).isEqualTo(expectedSource)
        assertThat(result.withDisabledPlugins(setOf(pluginId))).isSameAs(result)
      }
    }
  }
}
