// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginInventoryChangeReason
import com.intellij.ide.plugins.newui.PluginModelEvent
import com.intellij.ide.plugins.newui.PluginOperationKind
import com.intellij.ide.plugins.newui.PluginOperationTerminalResult
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
internal class UnifiedPluginLocalSourceCoordinatorTest {
  @Test
  fun `initial state loads empty visible local sections`() = runTest {
    val provider = FakeLocalDataProvider(inventory())
    val coordinator = coordinator(provider)
    assertThat(coordinator.state.value.sections.map { it.status }).allMatch { it is PluginSectionStatus.Loading }

    coordinator.start()
    runCurrent()

    val state = coordinator.state.value
    assertThat(state.sections.map { it.id }).containsExactly(PluginSectionId.Installed, PluginSectionId.Bundled)
    assertThat(state.sections.map { it.status }).containsOnly(PluginSectionStatus.Ready)
    assertThat(state.sections.map { it.count }).containsExactly(0, 0)
    assertThat(provider.loadCount).isEqualTo(1)
    coordinator.close()
  }

  @Test
  fun `refresh rejects a stale non-cooperative completion`() = runTest {
    val first = CompletableDeferred<UnifiedPluginInventory>()
    val second = CompletableDeferred<UnifiedPluginInventory>()
    val provider = FakeLocalDataProvider(inventory()) { call ->
      if (call == 1) {
        try {
          first.await()
        }
        catch (_: CancellationException) {
          withContext(NonCancellable) { first.await() }
        }
      }
      else {
        second.await()
      }
    }
    val coordinator = coordinator(provider)
    coordinator.start()
    runCurrent()

    coordinator.refresh()
    runCurrent()
    second.complete(inventory(installed = listOf(inventoryItem("new.plugin", "New"))))
    runCurrent()
    first.complete(inventory(installed = listOf(inventoryItem("stale.plugin", "Stale"))))
    runCurrent()

    val installed = coordinator.state.value.sections.single { it.id == PluginSectionId.Installed }
    assertThat(installed.items.map { it.pluginId.idString }).containsExactly("new.plugin")
    assertThat(installed.items.single().contentRevision).isEqualTo(2)
    assertThat(provider.loadCount).isEqualTo(2)
    coordinator.close()
  }

  @Test
  fun `an update event re-enriches without reloading inventory`() = runTest {
    val updates = MutableSharedFlow<PluginUpdatesEvent>(extraBufferCapacity = 1)
    val provider = FakeLocalDataProvider(
      inventory(installed = listOf(inventoryItem("custom.plugin", "Custom"))),
    )
    val coordinator = coordinator(provider, updates = updates)
    coordinator.start()
    runCurrent()

    val update = plugin("custom.plugin", "Update")
    assertThat(updates.tryEmit(PluginUpdatesEvent(listOf(update), emptyList(), emptyList()))).isTrue()
    runCurrent()

    val item = coordinator.state.value.sections.single { it.id == PluginSectionId.Installed }.items.single()
    assertThat(item.rowInput?.updateDescriptor).isSameAs(update)
    assertThat(item.contentRevision).isEqualTo(2)
    assertThat(provider.loadCount).isEqualTo(1)
    assertThat(provider.enrichCount).isEqualTo(2)
    coordinator.close()
  }

  @Test
  fun `Update All targets appear in Installing until the controller removes them`() = runTest {
    val installed = inventoryItem("custom.plugin", "Installed")
    val provider = FakeLocalDataProvider(inventory(installed = listOf(installed)))
    val coordinator = coordinator(provider)
    coordinator.start()
    runCurrent()
    val update = plugin("custom.plugin", "Update")

    coordinator.setUpdateAllTargets(
      listOf(UnifiedPluginUpdateAllTarget(update, UnifiedPluginUpdateAllTargetStatus.Active))
    )
    runCurrent()

    val item = coordinator.state.value.sections.single { it.id == PluginSectionId.Installing }.items.single()
    assertThat(item.modelHandle?.model).isSameAs(update)
    assertThat(item.rowInput?.installedPlugin?.pluginId).isEqualTo(update.pluginId)
    assertThat(item.rowInput?.updateDescriptor).isSameAs(update)
    assertThat(item.rowInput?.operationInProgress).isTrue()

    coordinator.setUpdateAllTargets(emptyList())
    runCurrent()
    assertThat(coordinator.state.value.sections.map { it.id }).doesNotContain(PluginSectionId.Installing)
    coordinator.close()
  }

  @Test
  fun `session operation replaces its Update All Installing projection`() = runTest {
    val events = MutableSharedFlow<PluginModelEvent>(extraBufferCapacity = 1)
    val installed = inventoryItem("custom.plugin", "Installed")
    val coordinator = coordinator(
      FakeLocalDataProvider(inventory(installed = listOf(installed))),
      hostEvents = events,
    )
    coordinator.start()
    runCurrent()
    val update = plugin("custom.plugin", "Update")
    coordinator.setUpdateAllTargets(
      listOf(UnifiedPluginUpdateAllTarget(update, UnifiedPluginUpdateAllTargetStatus.Active))
    )
    runCurrent()

    events.tryEmit(operationStarted(UUID.randomUUID(), update, kind = PluginOperationKind.UPDATE))
    runCurrent()

    val item = coordinator.state.value.sections.single { it.id == PluginSectionId.Installing }.items.single()
    assertThat(item.rowInput?.updateDescriptor).isNull()
    assertThat(item.rowInput?.operationInProgress).isTrue()
    coordinator.close()
  }

  @Test
  fun `an update event does not replace a pending refresh with stale inventory`() = runTest {
    val refreshedInventory = CompletableDeferred<UnifiedPluginInventory>()
    val oldInventory = inventory(installed = listOf(inventoryItem("old.plugin", "Old")))
    val updates = MutableSharedFlow<PluginUpdatesEvent>(extraBufferCapacity = 1)
    val provider = FakeLocalDataProvider(oldInventory) { call ->
      if (call == 1) oldInventory else refreshedInventory.await()
    }
    val coordinator = coordinator(provider, updates = updates)
    coordinator.start()
    runCurrent()

    coordinator.refresh()
    runCurrent()
    val update = plugin("new.plugin", "New update")
    assertThat(updates.tryEmit(PluginUpdatesEvent(listOf(update), emptyList(), emptyList()))).isTrue()
    runCurrent()

    assertThat(coordinator.state.value.sections.map { it.status }).allMatch { it is PluginSectionStatus.Loading }
    assertThat(provider.enrichCount).isEqualTo(1)
    refreshedInventory.complete(inventory(installed = listOf(inventoryItem("new.plugin", "New"))))
    runCurrent()

    val item = coordinator.state.value.sections.single { it.id == PluginSectionId.Installed }.items.single()
    assertThat(item.pluginId.idString).isEqualTo("new.plugin")
    assertThat(item.rowInput?.updateDescriptor).isSameAs(update)
    assertThat(provider.loadCount).isEqualTo(2)
    assertThat(provider.enrichCount).isEqualTo(3)
    coordinator.close()
  }

  @Test
  fun `failure keeps empty sections visible with a retryable error`() = runTest {
    val provider = FakeLocalDataProvider(inventory()) { throw IllegalStateException("load failed") }
    val coordinator = coordinator(provider)
    coordinator.start()
    runCurrent()

    val state = coordinator.state.value
    assertThat(state.sections.map { it.count }).containsExactly(0, 0)
    assertThat(state.sections.map { it.status }).allSatisfy { status ->
      assertThat(status).isEqualTo(
        PluginSectionStatus.Failed(PluginSectionError("Unable to load plugins", retryable = true))
      )
    }
    coordinator.close()
  }

  @Test
  fun `failed refresh remains degraded after update enrichment`() = runTest {
    val staleInventory = inventory(installed = listOf(inventoryItem("custom.plugin", "Custom")))
    val updates = MutableSharedFlow<PluginUpdatesEvent>(extraBufferCapacity = 1)
    val provider = FakeLocalDataProvider(staleInventory) { call ->
      if (call == 1) staleInventory else throw IllegalStateException("refresh failed")
    }
    val coordinator = coordinator(provider, updates = updates)
    coordinator.start()
    runCurrent()

    coordinator.refresh()
    runCurrent()

    val error = PluginSectionError("Unable to load plugins", retryable = true)
    assertThat(coordinator.state.value.sections.map { it.status })
      .containsOnly(PluginSectionStatus.Degraded(error))
    assertThat(coordinator.state.value.sections.single { it.id == PluginSectionId.Installed }.items)
      .hasSize(1)

    val update = plugin("custom.plugin", "Update")
    assertThat(updates.tryEmit(PluginUpdatesEvent(listOf(update), emptyList(), emptyList()))).isTrue()
    runCurrent()

    val state = coordinator.state.value
    assertThat(state.sections.map { it.status }).containsOnly(PluginSectionStatus.Degraded(error))
    assertThat(state.sections.single { it.id == PluginSectionId.Installed }.items.single().rowInput?.updateDescriptor)
      .isSameAs(update)
    assertThat(provider.loadCount).isEqualTo(2)
    assertThat(provider.enrichCount).isEqualTo(2)
    coordinator.close()
  }

  @Test
  fun `partial side inventory keeps usable rows with a retryable degraded status`() = runTest {
    val partialInventory = inventory(
      installed = listOf(inventoryItem("remote.plugin", "Remote")),
      unavailableSides = setOf(PluginSource.LOCAL),
    )
    val coordinator = coordinator(FakeLocalDataProvider(partialInventory))

    coordinator.start()
    runCurrent()

    val state = coordinator.state.value
    assertThat(state.sections.single { it.id == PluginSectionId.Installed }.items.map { it.pluginId.idString })
      .containsExactly("remote.plugin")
    assertThat(state.sections.map { it.status }).containsOnly(
      PluginSectionStatus.Degraded(PluginSectionError("Unable to load plugins", retryable = true))
    )
    coordinator.close()
  }

  @Test
  fun `host events have one collector and invalidate inventory`() = runTest {
    val events = MutableSharedFlow<PluginModelEvent>(extraBufferCapacity = 1)
    var collectorCount = 0
    val countedEvents = flow {
      collectorCount++
      events.collect { emit(it) }
    }
    val provider = FakeLocalDataProvider(inventory())
    val coordinator = coordinator(provider, hostEvents = countedEvents)
    coordinator.start()
    runCurrent()

    assertThat(events.tryEmit(invalidation())).isTrue()
    runCurrent()

    assertThat(collectorCount).isEqualTo(1)
    assertThat(provider.loadCount).isEqualTo(2)
    coordinator.close()
  }

  @Test
  fun `accepted install appears first without enabling initial selection`() = runTest {
    val events = MutableSharedFlow<PluginModelEvent>(extraBufferCapacity = 1)
    val coordinator = coordinator(FakeLocalDataProvider(inventory()), hostEvents = events)
    coordinator.start()
    runCurrent()
    val model = plugin("installing.plugin", "Installing")

    assertThat(events.tryEmit(operationStarted(UUID.randomUUID(), model))).isTrue()
    runCurrent()

    val state = coordinator.state.value
    assertThat(state.sections.map { it.id }).containsExactly(
      PluginSectionId.Installing,
      PluginSectionId.Installed,
      PluginSectionId.Bundled,
    )
    assertThat(state.sections.first().items.single().modelHandle?.model).isSameAs(model)
    assertThat(state.mayEstablishSelection).isFalse()
    coordinator.close()
  }

  @Test
  fun `manual update state changes from downloading to prepared`() = runTest {
    val events = MutableSharedFlow<PluginModelEvent>(extraBufferCapacity = 2)
    val coordinator = coordinator(FakeLocalDataProvider(inventory()), hostEvents = events)
    coordinator.start()
    runCurrent()
    val update = plugin("updated.plugin", "Updated Plugin")
    val operationId = UUID.randomUUID()

    assertThat(events.tryEmit(operationStarted(operationId, update, kind = PluginOperationKind.UPDATE))).isTrue()
    runCurrent()

    val downloading = coordinator.state.value.manualUpdates.getValue(update.pluginId)
    assertThat(downloading.operationId).isEqualTo(operationId)
    assertThat(downloading.presentation).isEqualTo(UnifiedPluginManualUpdatePresentation.Downloading)
    assertThat(coordinator.state.value.sections.single { it.id == PluginSectionId.Installing }.items.single().modelHandle?.model)
      .isSameAs(update)

    assertThat(events.tryEmit(operationFinished(
      operationId,
      update.pluginId,
      kind = PluginOperationKind.UPDATE,
      restartRequired = true,
    ))).isTrue()
    runCurrent()

    assertThat(coordinator.state.value.manualUpdates.getValue(update.pluginId).presentation)
      .isEqualTo(UnifiedPluginManualUpdatePresentation.Prepared(restartRequired = true))
    coordinator.close()
  }

  @Test
  fun `manual update ignores stale completion and clears on failure or reset`() = runTest {
    val events = MutableSharedFlow<PluginModelEvent>(extraBufferCapacity = 8)
    val coordinator = coordinator(FakeLocalDataProvider(inventory()), hostEvents = events)
    coordinator.start()
    runCurrent()
    val update = plugin("updated.plugin", "Updated Plugin")
    val firstOperationId = UUID.randomUUID()
    val secondOperationId = UUID.randomUUID()

    events.tryEmit(operationStarted(firstOperationId, update, kind = PluginOperationKind.UPDATE))
    events.tryEmit(operationStarted(secondOperationId, update, kind = PluginOperationKind.UPDATE))
    events.tryEmit(operationFinished(firstOperationId, update.pluginId, kind = PluginOperationKind.UPDATE))
    runCurrent()

    val active = coordinator.state.value.manualUpdates.getValue(update.pluginId)
    assertThat(active.operationId).isEqualTo(secondOperationId)
    assertThat(active.presentation).isEqualTo(UnifiedPluginManualUpdatePresentation.Downloading)

    events.tryEmit(operationFinished(
      secondOperationId,
      update.pluginId,
      kind = PluginOperationKind.UPDATE,
      result = PluginOperationTerminalResult.FAILED,
    ))
    runCurrent()
    assertThat(coordinator.state.value.manualUpdates).isEmpty()

    val thirdOperationId = UUID.randomUUID()
    events.tryEmit(operationStarted(thirdOperationId, update, kind = PluginOperationKind.UPDATE))
    events.tryEmit(operationFinished(
      thirdOperationId,
      update.pluginId,
      kind = PluginOperationKind.UPDATE,
      result = PluginOperationTerminalResult.CANCELLED,
    ))
    runCurrent()
    assertThat(coordinator.state.value.manualUpdates).isEmpty()

    val fourthOperationId = UUID.randomUUID()
    events.tryEmit(operationStarted(fourthOperationId, update, kind = PluginOperationKind.UPDATE))
    events.tryEmit(operationFinished(fourthOperationId, update.pluginId, kind = PluginOperationKind.UPDATE))
    runCurrent()
    assertThat(coordinator.state.value.manualUpdates).isNotEmpty()

    events.tryEmit(PluginModelEvent.InventoryInvalidated(PluginInventoryChangeReason.RESET, setOf(update.pluginId)))
    runCurrent()
    assertThat(coordinator.state.value.manualUpdates).isEmpty()
    coordinator.close()
  }

  @Test
  fun `apply clears prepared updates and keeps active downloads`() = runTest {
    val updates = MutableSharedFlow<PluginUpdatesEvent>(extraBufferCapacity = 2)
    val events = MutableSharedFlow<PluginModelEvent>(extraBufferCapacity = 8)
    val installed = listOf(
      inventoryItem("prepared.plugin", "Prepared Plugin"),
      inventoryItem("downloading.plugin", "Downloading Plugin"),
    )
    val coordinator = coordinator(
      FakeLocalDataProvider(inventory(installed = installed)),
      updates = updates,
      hostEvents = events,
    )
    coordinator.start()
    runCurrent()

    val preparedUpdate = plugin("prepared.plugin", "Prepared Update")
    val downloadingUpdate = plugin("downloading.plugin", "Downloading Update")
    updates.tryEmit(PluginUpdatesEvent(listOf(preparedUpdate, downloadingUpdate), emptyList(), emptyList()))
    val preparedOperationId = UUID.randomUUID()
    val downloadingOperationId = UUID.randomUUID()
    events.tryEmit(operationStarted(preparedOperationId, preparedUpdate, kind = PluginOperationKind.UPDATE))
    events.tryEmit(operationFinished(preparedOperationId, preparedUpdate.pluginId, kind = PluginOperationKind.UPDATE))
    events.tryEmit(operationStarted(downloadingOperationId, downloadingUpdate, kind = PluginOperationKind.UPDATE))
    runCurrent()

    events.tryEmit(PluginModelEvent.InventoryInvalidated(PluginInventoryChangeReason.APPLY, setOf(preparedUpdate.pluginId)))
    val nextPreparedUpdate = plugin("prepared.plugin", "Next Prepared Update")
    updates.tryEmit(PluginUpdatesEvent(listOf(nextPreparedUpdate, downloadingUpdate), emptyList(), emptyList()))
    runCurrent()

    val manualUpdates = coordinator.state.value.manualUpdates
    assertThat(manualUpdates).containsOnlyKeys(downloadingUpdate.pluginId)
    assertThat(manualUpdates.getValue(downloadingUpdate.pluginId).presentation)
      .isEqualTo(UnifiedPluginManualUpdatePresentation.Downloading)
    val preparedItem = coordinator.state.value.sections
      .single { it.id == PluginSectionId.Installed }
      .items.single { it.pluginId == preparedUpdate.pluginId }
    assertThat(preparedItem.rowInput?.updateDescriptor).isSameAs(nextPreparedUpdate)
    coordinator.close()
  }

  @Test
  fun `terminal install remains while destination inventory refreshes`() = runTest {
    val events = MutableSharedFlow<PluginModelEvent>(extraBufferCapacity = 4)
    val provider = FakeLocalDataProvider(inventory())
    val coordinator = coordinator(provider, hostEvents = events)
    coordinator.start()
    runCurrent()
    val model = plugin("installed.plugin", "Installed")
    val operationId = UUID.randomUUID()
    events.tryEmit(operationStarted(operationId, model))
    runCurrent()

    provider.inventory = inventory(installed = listOf(inventoryItem("installed.plugin", "Installed")))
    events.tryEmit(operationFinished(operationId, model.pluginId))
    events.tryEmit(invalidation())
    runCurrent()

    val state = coordinator.state.value
    assertThat(state.sections.single { it.id == PluginSectionId.Installing }.items.map { it.pluginId })
      .containsExactly(model.pluginId)
    assertThat(state.sections.single { it.id == PluginSectionId.Installed }.items.map { it.pluginId })
      .containsExactly(model.pluginId)
    coordinator.close()
  }

  @Test
  fun `another model session is ignored and a current update creates installing`() = runTest {
    val events = MutableSharedFlow<PluginModelEvent>(extraBufferCapacity = 2)
    val coordinator = coordinator(FakeLocalDataProvider(inventory()), hostEvents = events)
    coordinator.start()
    runCurrent()
    val model = plugin("plugin.id", "Plugin")

    events.tryEmit(operationStarted(UUID.randomUUID(), model, sessionId = "other"))
    runCurrent()

    assertThat(coordinator.state.value.sections.map { it.id }).doesNotContain(PluginSectionId.Installing)

    events.tryEmit(operationStarted(UUID.randomUUID(), model, kind = PluginOperationKind.UPDATE))
    runCurrent()

    assertThat(coordinator.state.value.sections.map { it.id }).contains(PluginSectionId.Installing)
    coordinator.close()
  }

  @Test
  fun `host event observer receives events from the ledger collector`() = runTest {
    val events = MutableSharedFlow<PluginModelEvent>(extraBufferCapacity = 2)
    val observed = mutableListOf<PluginModelEvent>()
    val coordinator = coordinator(
      FakeLocalDataProvider(inventory()),
      hostEvents = events,
      hostEventObserver = observed::add,
    )
    coordinator.start()
    runCurrent()
    val model = plugin("plugin.id", "Plugin")
    val operationId = UUID.randomUUID()
    val started = operationStarted(operationId, model, kind = PluginOperationKind.UPDATE)
    val finished = operationFinished(operationId, model.pluginId, kind = PluginOperationKind.UPDATE)

    events.tryEmit(started)
    events.tryEmit(finished)
    runCurrent()

    assertThat(observed).containsExactly(started, finished)
    coordinator.close()
  }

  private fun TestScope.coordinator(
    provider: UnifiedPluginLocalDataProvider,
    updates: Flow<PluginUpdatesEvent> = emptyFlow(),
    hostEvents: Flow<PluginModelEvent> = flow { awaitCancellation() },
    hostEventObserver: (PluginModelEvent) -> Unit = {},
  ): UnifiedPluginLocalSourceCoordinator {
    return UnifiedPluginLocalSourceCoordinator(
      scope = backgroundScope,
      dataProvider = provider,
      updates = updates,
      hostEvents = hostEvents,
      sessionId = SESSION_ID,
      loadErrorMessage = "Unable to load plugins",
      hostEventObserver = hostEventObserver,
    )
  }

  private class FakeLocalDataProvider(
    var inventory: UnifiedPluginInventory,
    private val load: (suspend (Int) -> UnifiedPluginInventory)? = null,
  ) : UnifiedPluginLocalDataProvider {
    var loadCount = 0
    var enrichCount = 0

    override suspend fun loadInventory(): UnifiedPluginInventory {
      loadCount++
      return load?.invoke(loadCount) ?: inventory
    }

    override suspend fun enrich(
      inventory: UnifiedPluginInventory,
      updates: PluginUpdatesEvent?,
      contentRevision: Long,
    ): UnifiedPluginLocalSnapshot {
      enrichCount++
      val plugins = inventory.installedPlugins + inventory.bundledPlugins
      return buildLocalSnapshot(
        inventory = inventory,
        updates = updates,
        contentRevision = contentRevision,
        enabledStates = plugins.associate { it.model.pluginId to true },
        errors = emptyMap(),
        installationStates = emptyMap(),
        restrictions = emptyMap(),
      )
    }
  }

  private fun inventory(
    installed: List<UnifiedPluginInventoryItem> = emptyList(),
    bundled: List<UnifiedPluginInventoryItem> = emptyList(),
    unavailableSides: Set<PluginSource> = emptySet(),
  ): UnifiedPluginInventory = UnifiedPluginInventory(installed, bundled, unavailableSides)

  private fun inventoryItem(id: String, name: String): UnifiedPluginInventoryItem {
    return UnifiedPluginInventoryItem(
      model = plugin(id, name),
      runtimeOn = PluginSource.LOCAL,
      stagedOn = null,
      bundledOn = null,
    )
  }

  private fun plugin(id: String, name: String): PluginDto = PluginDto(name, PluginId.getId(id))

  private fun invalidation(): PluginModelEvent.InventoryInvalidated {
    return PluginModelEvent.InventoryInvalidated(PluginInventoryChangeReason.ENABLE_DISABLE, emptySet())
  }

  private fun operationStarted(
    operationId: UUID,
    model: PluginDto,
    sessionId: String = SESSION_ID,
    kind: PluginOperationKind = PluginOperationKind.INSTALL,
  ): PluginModelEvent.OperationStarted {
    return PluginModelEvent.OperationStarted(
      sessionId, operationId, model.pluginId, model, PluginSource.LOCAL, kind
    )
  }

  private fun operationFinished(
    operationId: UUID,
    pluginId: PluginId,
    sessionId: String = SESSION_ID,
    kind: PluginOperationKind = PluginOperationKind.INSTALL,
    result: PluginOperationTerminalResult = PluginOperationTerminalResult.SUCCEEDED,
    restartRequired: Boolean = false,
  ): PluginModelEvent.OperationFinished {
    return PluginModelEvent.OperationFinished(
      sessionId,
      operationId,
      pluginId,
      PluginSource.LOCAL,
      kind,
      result,
      restartRequired = restartRequired,
    )
  }

  private companion object {
    const val SESSION_ID: String = "session"
  }
}
