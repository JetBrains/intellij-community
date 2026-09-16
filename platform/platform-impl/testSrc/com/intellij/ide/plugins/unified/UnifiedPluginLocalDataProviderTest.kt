// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginInstallationState
import com.intellij.ide.plugins.newui.calculateTags
import com.intellij.ide.plugins.newui.PluginRowInput
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.ide.plugins.newui.PluginStatus
import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@TestApplication
internal class UnifiedPluginLocalDataProviderTest {
  @Test
  fun `local snapshot enriches row inputs and list model data`() {
    val installedModel = plugin("custom.plugin", "Custom").apply { vendor = "JetBrains" }
    val installed = UnifiedPluginInventoryItem(installedModel, PluginSource.LOCAL, null, null)
    val bundled = inventoryItem("bundled.plugin", "Bundled", runtimeOn = PluginSource.REMOTE, bundledOn = PluginSource.REMOTE)
    val staged = inventoryItem("staged.plugin", "Staged", stagedOn = PluginSource.LOCAL)
    val inventory = UnifiedPluginInventory(listOf(installed, staged), listOf(bundled))
    val installedError = HtmlChunk.text("broken")
    val installedState = PluginInstallationState(true, PluginStatus.INSTALLED_WITHOUT_RESTART)
    val update = plugin("custom.plugin", "Custom update")

    val snapshot = buildLocalSnapshot(
      inventory = inventory,
      updates = PluginUpdatesEvent(listOf(update), emptyList(), emptyList()),
      contentRevision = 7,
      enabledStates = mapOf(
        installed.model.pluginId to false,
        bundled.model.pluginId to true,
        staged.model.pluginId to true,
      ),
      errors = mapOf(
        installed.model.pluginId to listOf(installedError),
        PluginId.getId("irrelevant.plugin") to listOf(HtmlChunk.text("irrelevant")),
      ),
      installationStates = mapOf(installed.model.pluginId to installedState),
      restrictions = mapOf(bundled.model.pluginId to true),
      tags = mapOf(installed.model.pluginId to listOf("Developer Tools")),
    )

    val installedItem = snapshot.installedItems.first()
    assertThat(installedItem.contentRevision).isEqualTo(7)
    assertThat(installedItem.modelHandle?.model).isSameAs(installed.model)
    assertThat(installedItem.searchVendor).isEqualTo("JetBrains")
    assertThat(installedItem.searchTags).containsExactly("Developer Tools")
    assertThat(installedItem.rowInput).isEqualTo(
      PluginRowInput(
        installedPlugin = installed.model,
        installationState = installedState,
        errors = listOf(installedError),
        updateDescriptor = update,
        enabled = false,
        restrictedByProduct = false,
      )
    )

    val bundledInput = snapshot.bundledItems.single().rowInput
    assertThat(bundledInput?.installationState).isEqualTo(PluginInstallationState(true))
    assertThat(bundledInput?.restrictedByProduct).isTrue()
    assertThat(snapshot.installedItems.last().rowInput?.installationState).isEqualTo(PluginInstallationState(false))
    assertThat(snapshot.listModelData.installedModels.keys).containsExactlyInAnyOrder(
      installed.model.pluginId,
      staged.model.pluginId,
      bundled.model.pluginId,
    )
    assertThat(snapshot.listModelData.errors).containsOnlyKeys(installed.model.pluginId)
    assertThat(snapshot.listModelData.installationStates).containsEntry(installed.model.pluginId, installedState)
      .containsEntry(staged.model.pluginId, PluginInstallationState(false))
      .containsEntry(bundled.model.pluginId, PluginInstallationState(true))
  }

  @Test
  fun `missing session enabled state is rejected`() {
    val installed = inventoryItem("custom.plugin", "Custom", runtimeOn = PluginSource.LOCAL)

    assertThatThrownBy {
      buildLocalSnapshot(
        inventory = UnifiedPluginInventory(listOf(installed), emptyList()),
        updates = null,
        contentRevision = 1,
        enabledStates = emptyMap(),
        errors = emptyMap(),
        installationStates = emptyMap(),
        restrictions = emptyMap(),
      )
    }.isInstanceOf(IllegalStateException::class.java)
      .hasMessageContaining("Missing enabled state")
  }

  @Test
  fun `bulk product restriction adds a product tag`() {
    val model = plugin("restricted.plugin", "Restricted")

    assertThat(model.calculateTags(requiresUltimateButDisabled = false)).isEmpty()
    assertThat(model.calculateTags(requiresUltimateButDisabled = true))
      .anyMatch { tag -> tag == "Ultimate" || tag == "Pro" }
  }

  @Test
  fun `local snapshot assigns bundled categories and normalizes a missing category`() {
    val installedModel = plugin("installed.plugin", "Installed").apply { displayCategory = "Productivity" }
    val installed = UnifiedPluginInventoryItem(
      installedModel,
      runtimeOn = PluginSource.LOCAL,
      stagedOn = null,
      bundledOn = null,
    )
    val categorizedModel = plugin("categorized.plugin", "Categorized").apply { displayCategory = "Developer Tools" }
    val categorized = UnifiedPluginInventoryItem(
      categorizedModel,
      runtimeOn = PluginSource.LOCAL,
      stagedOn = null,
      bundledOn = PluginSource.LOCAL,
    )
    val uncategorized = inventoryItem(
      "uncategorized.plugin",
      "Uncategorized",
      runtimeOn = PluginSource.LOCAL,
      bundledOn = PluginSource.LOCAL,
    )
    val bundled = listOf(categorized, uncategorized)
    val allPlugins = listOf(installed) + bundled

    val snapshot = buildLocalSnapshot(
      inventory = UnifiedPluginInventory(listOf(installed), bundled),
      updates = null,
      contentRevision = 1,
      enabledStates = allPlugins.associate { it.model.pluginId to true },
      errors = emptyMap(),
      installationStates = emptyMap(),
      restrictions = emptyMap(),
    )

    assertThat(snapshot.bundledItems.map(PluginItemState::searchCategory)).containsExactly(
      "Developer Tools",
      IdeBundle.message("plugins.configurable.other.bundled"),
    )
    assertThat(snapshot.installedItems.single().searchCategory).isEqualTo("Productivity")
  }

  @Test
  fun `plugins with loading errors appear first in local sections without reordering peers`() {
    val installedHealthyFirst = inventoryItem("installed.healthy.first", "Installed healthy first", runtimeOn = PluginSource.LOCAL)
    val installedBrokenFirst = inventoryItem("installed.broken.first", "Installed broken first", runtimeOn = PluginSource.LOCAL)
    val installedBrokenSecond = inventoryItem("installed.broken.second", "Installed broken second", runtimeOn = PluginSource.LOCAL)
    val installedHealthySecond = inventoryItem("installed.healthy.second", "Installed healthy second", runtimeOn = PluginSource.LOCAL)
    val bundledHealthyFirst = inventoryItem(
      "bundled.healthy.first",
      "Bundled healthy first",
      runtimeOn = PluginSource.LOCAL,
      bundledOn = PluginSource.LOCAL,
    )
    val bundledBroken = inventoryItem(
      "bundled.broken",
      "Bundled broken",
      runtimeOn = PluginSource.LOCAL,
      bundledOn = PluginSource.LOCAL,
    )
    val bundledHealthySecond = inventoryItem(
      "bundled.healthy.second",
      "Bundled healthy second",
      runtimeOn = PluginSource.LOCAL,
      bundledOn = PluginSource.LOCAL,
    )
    val installed = listOf(installedHealthyFirst, installedBrokenFirst, installedBrokenSecond, installedHealthySecond)
    val bundled = listOf(bundledHealthyFirst, bundledBroken, bundledHealthySecond)
    val allPlugins = installed + bundled

    val snapshot = buildLocalSnapshot(
      inventory = UnifiedPluginInventory(installed, bundled),
      updates = null,
      contentRevision = 1,
      enabledStates = allPlugins.associate { it.model.pluginId to true },
      errors = mapOf(
        installedBrokenFirst.model.pluginId to listOf(HtmlChunk.text("broken")),
        installedBrokenSecond.model.pluginId to listOf(HtmlChunk.text("broken")),
        bundledBroken.model.pluginId to listOf(HtmlChunk.text("broken")),
      ),
      installationStates = emptyMap(),
      restrictions = emptyMap(),
    )

    assertThat(snapshot.installedItems.map { it.pluginId.idString }).containsExactly(
      "installed.broken.first",
      "installed.broken.second",
      "installed.healthy.first",
      "installed.healthy.second",
    )
    assertThat(snapshot.bundledItems.map { it.pluginId.idString }).containsExactly(
      "bundled.broken",
      "bundled.healthy.first",
      "bundled.healthy.second",
    )
  }

  @Test
  fun `degraded snapshot keeps facts available from the surviving side`() {
    val installedModel = plugin("custom.plugin", "Custom").apply { isEnabled = false }
    val installed = UnifiedPluginInventoryItem(installedModel, PluginSource.LOCAL, null, null)
    val staged = inventoryItem("staged.plugin", "Staged", stagedOn = PluginSource.LOCAL)
    val bundled = inventoryItem("bundled.plugin", "Bundled", runtimeOn = PluginSource.LOCAL, bundledOn = PluginSource.LOCAL)
    val update = plugin("custom.plugin", "Custom update")
    val inventory = UnifiedPluginInventory(
      installedPlugins = listOf(installed, staged),
      bundledPlugins = listOf(bundled),
      unavailableSides = setOf(PluginSource.REMOTE),
    )

    val snapshot = buildDegradedLocalSnapshot(
      inventory = inventory,
      updates = PluginUpdatesEvent(listOf(update), emptyList(), emptyList()),
      contentRevision = 9,
    )

    assertThat(snapshot.installedItems).hasSize(2)
    assertThat(snapshot.bundledItems).hasSize(1)
    val installedInput = snapshot.installedItems.first().rowInput
    assertThat(installedInput?.enabled).isFalse()
    assertThat(installedInput?.updateDescriptor).isSameAs(update)
    assertThat(installedInput?.errors).isEmpty()
    assertThat(installedInput?.restrictedByProduct).isFalse()
    assertThat(installedInput?.installationState).isEqualTo(PluginInstallationState(true))
    assertThat(snapshot.installedItems.last().rowInput?.installationState).isEqualTo(PluginInstallationState(false))
    assertThat(snapshot.listModelData.errors).isEmpty()
  }

  private fun inventoryItem(
    id: String,
    name: String,
    runtimeOn: PluginSource? = null,
    stagedOn: PluginSource? = null,
    bundledOn: PluginSource? = null,
  ): UnifiedPluginInventoryItem {
    return UnifiedPluginInventoryItem(plugin(id, name), runtimeOn, stagedOn, bundledOn)
  }

  private fun plugin(id: String, name: String): PluginDto = PluginDto(name, PluginId.getId(id))
}
