// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.newui.PluginNodeModelBuilderFactory
import com.intellij.openapi.extensions.PluginId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

internal class UnifiedPluginsPageStateTest {
  @Test
  fun `model handles compare by mutable model identity`() {
    val model = pluginModel("plugin.id")

    assertThat(PluginItemModelHandle(model)).isEqualTo(PluginItemModelHandle(model))
    assertThat(PluginItemModelHandle(pluginModel("plugin.id"))).isNotEqualTo(PluginItemModelHandle(model))
  }

  @Test
  fun `content revision changes item equality for the same model`() {
    val pluginId = PluginId.getId("plugin.id")
    val handle = PluginItemModelHandle(pluginModel(pluginId.idString))
    val item = PluginItemState(pluginId, "Plugin", contentRevision = 1, modelHandle = handle)

    assertThat(item.copy(contentRevision = 2)).isNotEqualTo(item)
    assertThat(item.copy()).isEqualTo(item)
  }

  @Test
  fun `visible item prefixes do not traverse the complete item list`() {
    val itemReads = AtomicInteger()
    val items = object : AbstractList<PluginItemState>() {
      override val size: Int = PluginSectionState.MAX_DISPLAYED_ITEM_COUNT + 1

      override fun get(index: Int): PluginItemState {
        itemReads.incrementAndGet()
        return PluginItemState(PluginId.getId("plugin.$index"), "Plugin $index")
      }
    }

    val section = PluginSectionState(PluginSectionId.Installed, items = items, expanded = true)

    assertThat(section.displayItems).hasSize(PluginSectionState.MAX_DISPLAYED_ITEM_COUNT)
    assertThat(section.visibleItems).hasSize(PluginSectionState.MAX_DISPLAYED_ITEM_COUNT)
    assertThat(itemReads).hasValue(0)
  }

  @Test
  fun `item rejects a model with a different plugin id`() {
    assertThatThrownBy {
      PluginItemState(
        PluginId.getId("item.id"),
        "Plugin",
        modelHandle = PluginItemModelHandle(pluginModel("model.id")),
      )
    }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("does not match item ID")
  }

  private fun pluginModel(pluginId: String) = PluginNodeModelBuilderFactory
    .createBuilder(PluginId.getId(pluginId))
    .setName("Plugin")
    .build()
}
