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
    val model = plugin(pluginId)
    val context = PluginOperationContext.create(pluginId, PluginSource.LOCAL, PluginOperationKind.INSTALL)

    publisher.operationStarted("session", context, model)
    publisher.operationTargetFinished(context, PluginSource.LOCAL, PluginOperationTerminalResult.SUCCEEDED)
    publisher.operationFinished(context)

    assertThat(events).containsExactly(
      PluginModelEvent.OperationStarted(
        "session", context.operationId, pluginId, model, PluginSource.LOCAL, PluginOperationKind.INSTALL
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
    val model = plugin(pluginId)
    val context = PluginOperationContext.create(pluginId, PluginSource.BOTH, PluginOperationKind.INSTALL)

    publisher.operationStarted("session", context, model)
    publisher.operationTargetFinished(context, PluginSource.LOCAL, PluginOperationTerminalResult.SUCCEEDED)
    publisher.operationStarted("session", context, model)
    publisher.operationTargetFinished(context, PluginSource.REMOTE, PluginOperationTerminalResult.SUCCEEDED)
    publisher.operationFinished(context)

    assertThat(events.filterIsInstance<PluginModelEvent.OperationStarted>()).hasSize(1)
    assertThat(events.filterIsInstance<PluginModelEvent.OperationFinished>().map { it.result })
      .containsExactly(PluginOperationTerminalResult.SUCCEEDED)
  }

  @Test
  fun `logical operation publishes the combined restart result`() {
    val events = mutableListOf<PluginModelEvent>()
    val publisher = PluginModelEventPublisher(events::add)
    val pluginId = PluginId.getId("plugin.id")
    val context = PluginOperationContext.create(pluginId, PluginSource.BOTH, PluginOperationKind.UPDATE)

    publisher.operationStarted("session", context, plugin(pluginId))
    publisher.operationTargetFinished(
      context,
      PluginSource.LOCAL,
      PluginOperationTerminalResult.SUCCEEDED,
      restartRequired = false,
    )
    publisher.operationTargetFinished(
      context,
      PluginSource.REMOTE,
      PluginOperationTerminalResult.SUCCEEDED,
      restartRequired = true,
    )
    publisher.operationFinished(context)

    assertThat(events.filterIsInstance<PluginModelEvent.OperationFinished>().single().restartRequired).isTrue()
  }

  @Test
  fun `failed operation does not publish a restart result`() {
    val events = mutableListOf<PluginModelEvent>()
    val publisher = PluginModelEventPublisher(events::add)
    val pluginId = PluginId.getId("plugin.id")
    val context = PluginOperationContext.create(pluginId, PluginSource.BOTH, PluginOperationKind.UPDATE)

    publisher.operationStarted("session", context, plugin(pluginId))
    publisher.operationTargetFinished(
      context,
      PluginSource.LOCAL,
      PluginOperationTerminalResult.FAILED,
      restartRequired = true,
    )
    publisher.operationFinished(context)

    val completion = events.filterIsInstance<PluginModelEvent.OperationFinished>().single()
    assertThat(completion.result).isEqualTo(PluginOperationTerminalResult.FAILED)
    assertThat(completion.restartRequired).isFalse()
  }

  @Test
  fun `installed dependencies are retained on the completed operation`() {
    val events = mutableListOf<PluginModelEvent>()
    val publisher = PluginModelEventPublisher(events::add)
    val pluginId = PluginId.getId("plugin.id")
    val dependency = plugin(PluginId.getId("dependency.id"))
    val context = PluginOperationContext.create(pluginId, PluginSource.LOCAL, PluginOperationKind.INSTALL)

    publisher.operationStarted("session", context, plugin(pluginId))
    publisher.operationTargetFinished(
      context,
      PluginSource.LOCAL,
      PluginOperationTerminalResult.SUCCEEDED,
      listOf(dependency, dependency),
    )
    publisher.operationFinished(context)

    assertThat(events.filterIsInstance<PluginModelEvent.OperationFinished>().single().installedPlugins)
      .containsExactly(dependency)
  }

  @Test
  fun `missing physical target completes as cancellation`() {
    val events = mutableListOf<PluginModelEvent>()
    val publisher = PluginModelEventPublisher(events::add)
    val context = PluginOperationContext.create(
      PluginId.getId("plugin.id"), PluginSource.BOTH, PluginOperationKind.UPDATE
    )

    publisher.operationStarted("session", context, plugin(context.displayPluginId))
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

    publisher.operationStarted("session", context, plugin(context.displayPluginId))
    publisher.operationTargetFinished(context, PluginSource.LOCAL, PluginOperationTerminalResult.FAILED)
    publisher.operationFinished(context)

    assertThat(events.filterIsInstance<PluginModelEvent.OperationFinished>().map { it.result })
      .containsExactly(PluginOperationTerminalResult.FAILED)
  }

  @Test
  fun `scheduled dependencies are published before completion and deduplicated`() {
    val events = mutableListOf<PluginModelEvent>()
    val publisher = PluginModelEventPublisher(events::add)
    val pluginId = PluginId.getId("plugin.id")
    val dependency = plugin(PluginId.getId("dependency.id"))
    val context = PluginOperationContext.create(pluginId, PluginSource.LOCAL, PluginOperationKind.INSTALL)

    publisher.operationStarted("session", context, plugin(pluginId))
    publisher.operationDependenciesScheduled(context, listOf(dependency, dependency, plugin(pluginId)))
    publisher.operationDependenciesScheduled(context, listOf(dependency))

    assertThat(events.filterIsInstance<PluginModelEvent.OperationDependenciesScheduled>()).containsExactly(
      PluginModelEvent.OperationDependenciesScheduled(
        "session", context.operationId, pluginId, listOf(dependency), PluginSource.LOCAL, PluginOperationKind.INSTALL
      )
    )
    assertThat(events).noneMatch { it is PluginModelEvent.OperationFinished }
  }

  @Test
  fun `operation rejects a presentation model with another display id`() {
    val publisher = PluginModelEventPublisher {}
    val context = PluginOperationContext.create(
      PluginId.getId("display.plugin"), PluginSource.LOCAL, PluginOperationKind.INSTALL
    )

    org.assertj.core.api.Assertions.assertThatThrownBy {
      publisher.operationStarted("session", context, plugin(PluginId.getId("other.plugin")))
    }.isInstanceOf(IllegalArgumentException::class.java)
  }

  private fun plugin(pluginId: PluginId): PluginUiModel {
    return PluginNodeModelBuilderFactory.createBuilder(pluginId).setName(pluginId.idString).build()
  }
}
