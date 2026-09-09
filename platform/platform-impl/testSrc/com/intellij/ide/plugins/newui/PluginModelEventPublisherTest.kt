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

  @Test
  fun `single target operation publishes one lifecycle`() {
    val events = mutableListOf<PluginModelEvent>()
    val publisher = PluginModelEventPublisher(events::add)
    val pluginId = PluginId.getId("plugin.id")
    val context = PluginOperationContext.create(pluginId, PluginSource.LOCAL, PluginOperationKind.INSTALL)

    publisher.operationStarted("session", context)
    publisher.operationTargetFinished(context, PluginSource.LOCAL, PluginOperationTerminalResult.SUCCEEDED)
    publisher.operationFinished(context)

    assertThat(events).containsExactly(
      PluginModelEvent.OperationStarted(
        "session", context.operationId, pluginId, PluginSource.LOCAL, PluginOperationKind.INSTALL
      ),
      PluginModelEvent.OperationFinished(
        "session", context.operationId, pluginId, PluginSource.LOCAL, PluginOperationKind.INSTALL,
        PluginOperationTerminalResult.SUCCEEDED,
      ),
    )
  }

  @Test
  fun `two physical targets publish one logical lifecycle`() {
    val events = mutableListOf<PluginModelEvent>()
    val publisher = PluginModelEventPublisher(events::add)
    val pluginId = PluginId.getId("plugin.id")
    val context = PluginOperationContext.create(pluginId, PluginSource.BOTH, PluginOperationKind.INSTALL)

    publisher.operationStarted("session", context)
    publisher.operationTargetFinished(context, PluginSource.LOCAL, PluginOperationTerminalResult.SUCCEEDED)
    publisher.operationStarted("session", context)
    publisher.operationTargetFinished(context, PluginSource.REMOTE, PluginOperationTerminalResult.SUCCEEDED)
    publisher.operationFinished(context)

    assertThat(events.filterIsInstance<PluginModelEvent.OperationStarted>()).hasSize(1)
    assertThat(events.filterIsInstance<PluginModelEvent.OperationFinished>().map { it.result })
      .containsExactly(PluginOperationTerminalResult.SUCCEEDED)
  }

  @Test
  fun `missing physical target completes as cancellation`() {
    val events = mutableListOf<PluginModelEvent>()
    val publisher = PluginModelEventPublisher(events::add)
    val context = PluginOperationContext.create(
      PluginId.getId("plugin.id"), PluginSource.BOTH, PluginOperationKind.UPDATE
    )

    publisher.operationStarted("session", context)
    publisher.operationTargetFinished(context, PluginSource.LOCAL, PluginOperationTerminalResult.SUCCEEDED)
    publisher.operationFinished(context)

    assertThat(events.filterIsInstance<PluginModelEvent.OperationFinished>().map { it.result })
      .containsExactly(PluginOperationTerminalResult.CANCELLED)
  }

  @Test
  fun `physical failure takes precedence over a missing target`() {
    val events = mutableListOf<PluginModelEvent>()
    val publisher = PluginModelEventPublisher(events::add)
    val context = PluginOperationContext.create(
      PluginId.getId("plugin.id"), PluginSource.BOTH, PluginOperationKind.INSTALL
    )

    publisher.operationStarted("session", context)
    publisher.operationTargetFinished(context, PluginSource.LOCAL, PluginOperationTerminalResult.FAILED)
    publisher.operationFinished(context)

    assertThat(events.filterIsInstance<PluginModelEvent.OperationFinished>().map { it.result })
      .containsExactly(PluginOperationTerminalResult.FAILED)
  }
}
