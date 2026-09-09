// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui

import com.intellij.openapi.extensions.PluginId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class PluginModelEventPublisherTest {
  @Test
  fun `published inventory event owns an immutable plugin id snapshot`() {
    val events = mutableListOf<PluginModelEvent>()
    val publisher = PluginModelEventPublisher(events::add)
    val pluginId = PluginId.getId("plugin.id")
    val ids = mutableSetOf(pluginId)

    publisher.inventoryInvalidated(PluginInventoryChangeReason.INSTALL, ids)
    ids.clear()

    assertThat(events).containsExactly(
      PluginModelEvent.InventoryInvalidated(PluginInventoryChangeReason.INSTALL, setOf(pluginId))
    )
  }

  @Test
  fun `empty plugin id set requests a complete inventory refresh`() {
    val events = mutableListOf<PluginModelEvent>()
    val publisher = PluginModelEventPublisher(events::add)

    publisher.inventoryInvalidated(PluginInventoryChangeReason.APPLY)

    assertThat(events).containsExactly(
      PluginModelEvent.InventoryInvalidated(PluginInventoryChangeReason.APPLY, emptySet())
    )
  }
}
