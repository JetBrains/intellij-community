// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginInventoryChangeReason
import com.intellij.ide.plugins.newui.PluginModelEvent
import com.intellij.ide.plugins.newui.PluginNodeModelBuilderFactory
import com.intellij.ide.plugins.newui.PluginOperationKind
import com.intellij.ide.plugins.newui.PluginOperationTerminalResult
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.openapi.extensions.PluginId
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

@TestApplication
internal class UnifiedPluginUpdateAllControllerTest {
  private val executor = RecordingExecutor()
  private val controller = UnifiedPluginUpdateAllController(executor)

  @Test
  fun `enabled snapshot excludes disabled updates and plugin nods`() {
    val enabled = plugin("enabled")

    val snapshot = enabledUpdateSnapshot(
      PluginUpdatesEvent(listOf(enabled), listOf(plugin("disabled")), listOf(plugin("nod")))
    )

    assertThat(snapshot).containsExactly(enabled)
  }

  @Test
  fun `button becomes available only for enabled updates`() {
    controller.acceptUpdates(updates())
    assertThat(controller.state.value.presentation).isEqualTo(UnifiedPluginUpdateAllPresentation.Hidden)

    controller.acceptUpdates(updates(plugin("first"), plugin("second")))

    assertThat(controller.state.value.presentation).isEqualTo(UnifiedPluginUpdateAllPresentation.Available)
  }

  @Test
  fun `request captures one snapshot before executor starts`() {
    val first = plugin("first")
    val second = plugin("second")
    controller.acceptUpdates(updates(first, second))
    executor.beforeExecute = {
      assertThat(controller.state.value.presentation).isEqualTo(UnifiedPluginUpdateAllPresentation.Running(0, 2))
      assertThat(controller.state.value.targets.map { it.model }).containsExactly(first, second)
    }

    controller.requestUpdateAll()

    val request = executor.requests.single()
    assertThat(request.generation).isEqualTo(1)
    assertThat(request.updates).containsExactly(first, second)
  }

  @Test
  fun `active targets reach the projection before executor starts`() {
    val calls = mutableListOf<String>()
    val orderedExecutor = RecordingExecutor().apply {
      beforeExecute = { calls.add("execute") }
    }
    val orderedController = UnifiedPluginUpdateAllController(orderedExecutor) { targets ->
      assertThat(targets.map { it.status }).containsOnly(UnifiedPluginUpdateAllTargetStatus.Active)
      calls.add("project")
    }
    orderedController.acceptUpdates(updates(plugin("first")))

    orderedController.requestUpdateAll()

    assertThat(calls).containsExactly("project", "execute")
  }

  @Test
  fun `update snapshots do not change the prepared count`() {
    val first = plugin("first")
    val second = plugin("second")
    controller.acceptUpdates(updates(first, second))
    controller.requestUpdateAll()

    controller.acceptUpdates(updates(second))
    controller.acceptUpdates(updates(first, second))

    assertThat(controller.state.value.presentation).isEqualTo(UnifiedPluginUpdateAllPresentation.Running(0, 2))
    assertThat(controller.state.value.targets.map { it.model }).containsExactly(first, second)
  }

  @Test
  fun `successful operation advances the prepared count`() {
    val first = plugin("first")
    val second = plugin("second")
    controller.acceptUpdates(updates(first, second))
    controller.requestUpdateAll()

    finishOperation(first, PluginOperationTerminalResult.SUCCEEDED)

    assertThat(controller.state.value.presentation).isEqualTo(UnifiedPluginUpdateAllPresentation.Running(1, 2))
    assertThat(controller.state.value.targets.map { it.model }).containsExactly(second)
  }

  @Test
  fun `operation result must match the bound operation`() {
    val target = plugin("target")
    val operationId = UUID.randomUUID()
    controller.acceptUpdates(updates(target))
    controller.requestUpdateAll()

    controller.acceptOperationEvent(operationFinished(target, UUID.randomUUID(), PluginOperationTerminalResult.SUCCEEDED))
    controller.acceptOperationEvent(operationStarted(target, operationId))
    controller.acceptOperationEvent(operationFinished(target, UUID.randomUUID(), PluginOperationTerminalResult.SUCCEEDED))

    assertThat(controller.state.value.presentation).isEqualTo(UnifiedPluginUpdateAllPresentation.Running(0, 1))

    controller.acceptOperationEvent(operationFinished(target, operationId, PluginOperationTerminalResult.SUCCEEDED))

    assertThat(controller.state.value.presentation).isEqualTo(UnifiedPluginUpdateAllPresentation.Updated)
  }

  @Test
  fun `executor operation identity rejects an unrelated operation`() {
    val target = plugin("target")
    val expectedOperationId = UUID.randomUUID()
    controller.acceptUpdates(updates(target))
    controller.requestUpdateAll()
    executor.callbacks.single().started(target.pluginId, expectedOperationId)

    val unrelatedOperationId = UUID.randomUUID()
    controller.acceptOperationEvent(operationStarted(target, unrelatedOperationId))
    controller.acceptOperationEvent(operationFinished(target, unrelatedOperationId, PluginOperationTerminalResult.SUCCEEDED))

    assertThat(controller.state.value.presentation).isEqualTo(UnifiedPluginUpdateAllPresentation.Running(0, 1))

    controller.acceptOperationEvent(operationFinished(target, expectedOperationId, PluginOperationTerminalResult.SUCCEEDED))

    assertThat(controller.state.value.presentation).isEqualTo(UnifiedPluginUpdateAllPresentation.Updated)
  }

  @Test
  fun `failed operation is retained after the other updates prepare`() {
    val first = plugin("first")
    val second = plugin("second")
    controller.acceptUpdates(updates(first, second))
    controller.requestUpdateAll()

    finishOperation(first, PluginOperationTerminalResult.FAILED)
    executor.callbacks.single().prepared(second.pluginId, restartRequired = false)

    assertThat(controller.state.value.presentation).isEqualTo(UnifiedPluginUpdateAllPresentation.Failed)
    assertThat(controller.state.value.targets).containsExactly(
      UnifiedPluginUpdateAllTarget(first, UnifiedPluginUpdateAllTargetStatus.Failed)
    )

    controller.requestUpdateAll()

    assertThat(executor.requests.map { it.generation }).containsExactly(1, 2)
    assertThat(executor.requests.last().updates).containsExactly(first)
  }

  @Test
  fun `cancelled operation is retained and enables retry`() {
    val target = plugin("target")
    controller.acceptUpdates(updates(target))
    controller.requestUpdateAll()

    finishOperation(target, PluginOperationTerminalResult.CANCELLED)

    assertThat(controller.state.value).isEqualTo(
      UnifiedPluginUpdateAllState(
        presentation = UnifiedPluginUpdateAllPresentation.Failed,
        targets = listOf(UnifiedPluginUpdateAllTarget(target, UnifiedPluginUpdateAllTargetStatus.Failed)),
      )
    )

    controller.requestUpdateAll()

    assertThat(executor.requests).hasSize(2)
    assertThat(executor.requests.last().updates).containsExactly(target)
  }

  @Test
  fun `restart requirement survives a retry`() {
    val first = plugin("first")
    val second = plugin("second")
    controller.acceptUpdates(updates(first, second))
    controller.requestUpdateAll()
    executor.callbacks.single().prepared(first.pluginId, restartRequired = true)
    finishOperation(second, PluginOperationTerminalResult.FAILED)
    controller.requestUpdateAll()

    executor.callbacks.last().prepared(second.pluginId, restartRequired = false)

    assertThat(controller.state.value.presentation).isEqualTo(UnifiedPluginUpdateAllPresentation.RestartRequired)
  }

  @Test
  fun `completion without restart shows updated state`() {
    val first = plugin("first")
    val second = plugin("second")
    controller.acceptUpdates(updates(first, second))
    controller.requestUpdateAll()

    executor.callbacks.single().prepared(first.pluginId, restartRequired = false)
    executor.callbacks.single().prepared(second.pluginId, restartRequired = false)

    assertThat(controller.state.value).isEqualTo(UnifiedPluginUpdateAllState(UnifiedPluginUpdateAllPresentation.Updated))
  }

  @Test
  fun `reset clears prepared state and restores snapshot availability`() {
    val target = plugin("target")
    controller.acceptUpdates(updates(target))
    controller.requestUpdateAll()
    executor.callbacks.single().prepared(target.pluginId, restartRequired = true)

    controller.acceptOperationEvent(
      PluginModelEvent.InventoryInvalidated(PluginInventoryChangeReason.RESET, setOf(target.pluginId))
    )

    assertThat(controller.state.value.presentation).isEqualTo(UnifiedPluginUpdateAllPresentation.Available)
  }

  @Test
  fun `stale callback cannot change a newer request`() {
    val target = plugin("target")
    controller.acceptUpdates(updates(target))
    controller.requestUpdateAll()
    val staleCallback = executor.callbacks.single()
    finishOperation(target, PluginOperationTerminalResult.FAILED)
    controller.requestUpdateAll()

    staleCallback.prepared(target.pluginId, restartRequired = true)

    assertThat(controller.state.value.presentation).isEqualTo(UnifiedPluginUpdateAllPresentation.Running(0, 1))
  }

  @Test
  fun `closed controller ignores executor callbacks`() {
    val target = plugin("target")
    controller.acceptUpdates(updates(target))
    controller.requestUpdateAll()
    controller.close()

    executor.callbacks.single().prepared(target.pluginId, restartRequired = true)

    assertThat(controller.state.value.presentation).isEqualTo(UnifiedPluginUpdateAllPresentation.Running(0, 1))
  }

  @Test
  fun `controller forwards operation events and closes the executor`() {
    val target = plugin("target")
    val operationId = UUID.randomUUID()
    controller.acceptUpdates(updates(target))
    controller.requestUpdateAll()
    val started = operationStarted(target, operationId)
    val finished = operationFinished(target, operationId, PluginOperationTerminalResult.SUCCEEDED)

    controller.acceptOperationEvent(started)
    controller.acceptOperationEvent(finished)
    controller.close()

    assertThat(executor.events).containsExactly(started, finished)
    assertThat(executor.closed).isTrue()
  }

  private fun finishOperation(
    plugin: PluginDto,
    result: PluginOperationTerminalResult,
    restartRequired: Boolean = false,
  ) {
    val operationId = UUID.randomUUID()
    controller.acceptOperationEvent(operationStarted(plugin, operationId))
    controller.acceptOperationEvent(operationFinished(plugin, operationId, result, restartRequired))
  }

  private fun operationStarted(plugin: PluginDto, operationId: UUID): PluginModelEvent.OperationStarted {
    return PluginModelEvent.OperationStarted(
      sessionId = "session",
      operationId = operationId,
      displayPluginId = plugin.pluginId,
      presentationModel = plugin,
      target = PluginSource.LOCAL,
      kind = PluginOperationKind.UPDATE,
    )
  }

  private fun operationFinished(
    plugin: PluginDto,
    operationId: UUID,
    result: PluginOperationTerminalResult,
    restartRequired: Boolean = false,
  ): PluginModelEvent.OperationFinished {
    return PluginModelEvent.OperationFinished(
      sessionId = "session",
      operationId = operationId,
      displayPluginId = plugin.pluginId,
      target = PluginSource.LOCAL,
      kind = PluginOperationKind.UPDATE,
      result = result,
      restartRequired = restartRequired,
    )
  }

  private fun updates(vararg enabled: PluginDto): PluginUpdatesEvent {
    return PluginUpdatesEvent(enabled.toList(), emptyList(), emptyList())
  }

  private fun plugin(id: String): PluginDto {
    val model = PluginNodeModelBuilderFactory.createBuilder(PluginId.getId(id)).setName(id).build()
    return PluginDto.fromModel(model)
  }

  private class RecordingExecutor : UnifiedPluginUpdateAllExecutor {
    val requests = mutableListOf<UnifiedPluginUpdateAllRequest>()
    val callbacks = mutableListOf<UnifiedPluginUpdateAllCallback>()
    val events = mutableListOf<PluginModelEvent>()
    var beforeExecute: () -> Unit = {}
    var closed = false

    override fun execute(request: UnifiedPluginUpdateAllRequest, callback: UnifiedPluginUpdateAllCallback) {
      beforeExecute()
      requests.add(request)
      callbacks.add(callback)
    }

    override fun acceptOperationEvent(event: PluginModelEvent) {
      events.add(event)
    }

    override fun close() {
      closed = true
    }
  }
}
