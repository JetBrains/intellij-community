// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginInventoryEntry
import com.intellij.ide.plugins.newui.PluginInventoryLoadResult
import com.intellij.ide.plugins.newui.PluginInventorySnapshot
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.openapi.extensions.PluginId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class UnifiedPluginInventoryTest {
  @Test
  fun `staged descriptor supersedes runtime presentation and keeps bundled origin`() {
    val runtime = entry("bundled.plugin", "Runtime", PluginSource.LOCAL, bundled = true)
    val staged = entry("bundled.plugin", "Update", PluginSource.LOCAL)

    val result = normalizePluginInventory(snapshot(runtime = listOf(runtime), staged = listOf(staged)))

    assertThat(result.installedPlugins).isEmpty()
    val plugin = result.bundledPlugins.single()
    assertThat(plugin.model).isSameAs(staged.model)
    assertThat(plugin.runtimeOn).isEqualTo(PluginSource.LOCAL)
    assertThat(plugin.stagedOn).isEqualTo(PluginSource.LOCAL)
    assertThat(plugin.bundledOn).isEqualTo(PluginSource.LOCAL)
  }

  @Test
  fun `updated bundled model keeps bundled origin without a runtime entry`() {
    val staged = entry("bundled.plugin", "Update", PluginSource.LOCAL, bundledUpdate = true)

    val result = normalizePluginInventory(snapshot(staged = listOf(staged)))

    assertThat(result.installedPlugins).isEmpty()
    assertThat(result.bundledPlugins.single().model).isSameAs(staged.model)
  }

  @Test
  fun `same id on both sides prefers local presentation and retains remote bundled origin`() {
    val local = entry("shared.plugin", "Client", PluginSource.LOCAL)
    val remote = entry("shared.plugin", "Host", PluginSource.REMOTE, bundled = true)

    val result = normalizePluginInventory(snapshot(runtime = listOf(local, remote)))

    assertThat(result.installedPlugins).isEmpty()
    val plugin = result.bundledPlugins.single()
    assertThat(plugin.model).isSameAs(local.model)
    assertThat(plugin.model.source).isEqualTo(PluginSource.BOTH)
    assertThat(plugin.runtimeOn).isEqualTo(PluginSource.BOTH)
    assertThat(plugin.bundledOn).isEqualTo(PluginSource.REMOTE)
  }

  @Test
  fun `different ids on different sides remain separate physical occurrences`() {
    val local = entry("client.plugin", "Client", PluginSource.LOCAL)
    val remote = entry("backend.plugin", "Backend", PluginSource.REMOTE)

    val result = normalizePluginInventory(snapshot(runtime = listOf(local, remote)))

    assertThat(result.installedPlugins.map { it.model.pluginId.idString })
      .containsExactly("client.plugin", "backend.plugin")
    assertThat(result.installedPlugins.map(UnifiedPluginInventoryItem::installedOn))
      .containsExactly(PluginSource.LOCAL, PluginSource.REMOTE)
  }

  @Test
  fun `load result retains an unavailable side with usable inventory`() {
    val remote = entry("backend.plugin", "Backend", PluginSource.REMOTE)

    val result = normalizePluginInventory(
      PluginInventoryLoadResult(
        snapshot(runtime = listOf(remote)),
        unavailableSides = setOf(PluginSource.LOCAL),
      )
    )

    assertThat(result.installedPlugins.single().model).isSameAs(remote.model)
    assertThat(result.unavailableSides).containsExactly(PluginSource.LOCAL)
  }

  @Test
  fun `custom plugin stays in installed section with its side facts`() {
    val plugin = entry("custom.plugin", "Custom", PluginSource.REMOTE)

    val result = normalizePluginInventory(snapshot(runtime = listOf(plugin)))

    assertThat(result.bundledPlugins).isEmpty()
    val item = result.installedPlugins.single()
    assertThat(item.model).isSameAs(plugin.model)
    assertThat(item.installedOn).isEqualTo(PluginSource.REMOTE)
    assertThat(item.bundledOn).isNull()
  }

  @Test
  fun `implementation detail plugin is excluded when either side marks it hidden`() {
    val local = entry("detail.plugin", "Client", PluginSource.LOCAL)
    val remote = entry("detail.plugin", "Host", PluginSource.REMOTE, implementationDetail = true)

    val result = normalizePluginInventory(snapshot(runtime = listOf(local, remote)))

    assertThat(result.installedPlugins).isEmpty()
    assertThat(result.bundledPlugins).isEmpty()
  }

  @Test
  fun `last staged descriptor wins deterministically within one side`() {
    val first = entry("staged.plugin", "First", PluginSource.LOCAL)
    val second = entry("staged.plugin", "Second", PluginSource.LOCAL)

    val result = normalizePluginInventory(snapshot(staged = listOf(first, second)))

    assertThat(result.installedPlugins.single().model).isSameAs(second.model)
  }

  private fun snapshot(
    runtime: List<PluginInventoryEntry> = emptyList(),
    staged: List<PluginInventoryEntry> = emptyList(),
  ): PluginInventorySnapshot = PluginInventorySnapshot(runtime, staged)

  private fun entry(
    id: String,
    name: String,
    side: PluginSource,
    bundled: Boolean = false,
    bundledUpdate: Boolean = false,
    implementationDetail: Boolean = false,
  ): PluginInventoryEntry {
    val model = PluginDto(name, PluginId.getId(id)).apply {
      source = side
      isBundled = bundled
      isBundledUpdate = bundledUpdate
      isImplementationDetail = implementationDetail
    }
    return PluginInventoryEntry(model, side)
  }
}
