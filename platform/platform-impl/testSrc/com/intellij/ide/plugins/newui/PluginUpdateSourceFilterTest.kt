// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.updateSettings.impl.PluginUpdateSourceServiceImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class PluginUpdateSourceFilterTest {
  private val sources = PluginUpdateSourceServiceImpl()
  private val sourceA = sources.createCustomRepositoryPluginUpdateSourceId("https://example.com/a.xml")
  private val sourceB = sources.createCustomRepositoryPluginUpdateSourceId("https://example.com/b.xml")
  private val sourceC = sources.createCustomRepositoryPluginUpdateSourceId("https://example.com/c.xml")

  @Test
  fun `pending sources suppress enabled and disabled updates without changing the shared snapshot`(): Unit = timeoutRunBlocking {
    val enabled = plugin("enabled")
    val disabled = plugin("disabled")
    val unchanged = plugin("unchanged")
    val snapshot = PluginUpdatesEvent(listOf(enabled, unchanged), listOf(disabled), listOf(enabled))
    val updates = MutableStateFlow(snapshot)
    val filter = PluginUpdateSourceFilter()

    filter.sourceChanged(enabled.pluginId, sourceA, sourceB)
    filter.sourceChanged(disabled.pluginId, sourceA, sourceB)

    val filtered = filter.filter(updates).first()
    assertThat(filtered.enabledUpdates).containsExactly(unchanged)
    assertThat(filtered.disabledUpdates).isEmpty()
    assertThat(filtered.pluginNods).containsExactly(enabled)
    assertThat(updates.value).isSameAs(snapshot)
    assertThat(PluginUpdateSourceFilter().filter(updates).first()).isSameAs(snapshot)
  }

  @Test
  fun `returning to the initial source restores updates after several source changes`(): Unit = timeoutRunBlocking {
    val plugin = plugin("plugin")
    val updates = MutableStateFlow(PluginUpdatesEvent(listOf(plugin), emptyList(), emptyList()))
    val filter = PluginUpdateSourceFilter()

    filter.sourceChanged(plugin.pluginId, sourceA, sourceA)
    assertThat(filter.filter(updates).first()).isSameAs(updates.value)
    filter.sourceChanged(plugin.pluginId, sourceA, sourceB)
    assertThat(filter.filter(updates).first().all).isEmpty()
    filter.sourceChanged(plugin.pluginId, sourceB, sourceC)
    assertThat(filter.filter(updates).first().all).isEmpty()
    filter.sourceChanged(plugin.pluginId, sourceC, sourceA)
    assertThat(filter.filter(updates).first()).isSameAs(updates.value)
  }

  @Test
  fun `unknown sources participate in pending changes`(): Unit = timeoutRunBlocking {
    val plugin = plugin("plugin")
    val updates = MutableStateFlow(PluginUpdatesEvent(listOf(plugin), emptyList(), emptyList()))
    val filter = PluginUpdateSourceFilter()

    for ((initial, selected) in listOf(null to sourceA, sourceA to null)) {
      filter.sourceChanged(plugin.pluginId, initial, selected)
      assertThat(filter.filter(updates).first().all).isEmpty()
      filter.sourceChanged(plugin.pluginId, selected, initial)
      assertThat(filter.filter(updates).first()).isSameAs(updates.value)
    }
  }

  @Test
  fun `reset restores the latest snapshot and persistence clears only its plugin`(): Unit = timeoutRunBlocking {
    val first = plugin("first")
    val second = plugin("second")
    val updates = MutableStateFlow(PluginUpdatesEvent(listOf(first, second), emptyList(), emptyList()))
    val filter = PluginUpdateSourceFilter()
    filter.sourceChanged(first.pluginId, sourceA, sourceB)
    filter.sourceChanged(second.pluginId, sourceA, sourceB)

    val newer = plugin("first").apply { version = "2.0" }
    updates.value = PluginUpdatesEvent(listOf(newer, second), emptyList(), emptyList())
    assertThat(filter.filter(updates).first().all).isEmpty()

    filter.sourcePersisted(first.pluginId)
    assertThat(filter.filter(updates).first().all).containsExactly(newer)
    filter.clear()
    assertThat(filter.filter(updates).first()).isSameAs(updates.value)

    filter.sourceChanged(first.pluginId, sourceB, sourceA)
    assertThat(filter.filter(updates).first().all).containsExactly(second)
  }

  private fun plugin(id: String): PluginDto = PluginDto(id, PluginId.getId(id))
}
