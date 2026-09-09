// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.newui.PluginInventoryChangeReason
import com.intellij.ide.plugins.newui.PluginModelEvent
import com.intellij.ide.plugins.newui.PluginNodeModelBuilderFactory
import com.intellij.ide.plugins.newui.PluginOperationContext
import com.intellij.ide.plugins.newui.PluginOperationKind
import com.intellij.ide.plugins.newui.PluginOperationTerminalResult
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.CancellationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

internal class UnifiedPluginUpdateAllExecutorTest {
  @Test
  fun `executor starts each update with its installed plugin`() {
    val firstInstalled = plugin("first", "First installed")
    val secondInstalled = plugin("second", "Second installed")
    val firstUpdate = plugin("first", "First update")
    val secondUpdate = plugin("second", "Second update")
    val started = mutableListOf<Pair<PluginUiModel, PluginUiModel>>()
    val callback = RecordingCallback()
    val executor = PageSessionPluginUpdateAllExecutor(
      operationContextFactory = ::operationContext,
      installedPluginProvider = { id ->
        mapOf(firstInstalled.pluginId to firstInstalled, secondInstalled.pluginId to secondInstalled)[id]
      },
      updateStarter = { installed, update, _ -> started.add(installed to update) },
      runOnEdt = { it() },
    )

    executor.execute(UnifiedPluginUpdateAllRequest(1, listOf(firstUpdate, secondUpdate)), callback)

    assertThat(started).containsExactly(firstInstalled to firstUpdate, secondInstalled to secondUpdate)
    assertThat(callback.failures).isEmpty()
  }

  @Test
  fun `executor reports target start failures and continues`() {
    val failedInstalled = plugin("failed", "Failed installed")
    val failedUpdate = plugin("failed", "Failed update")
    val missingUpdate = plugin("missing", "Missing update")
    val successfulInstalled = plugin("successful", "Successful installed")
    val successfulUpdate = plugin("successful", "Successful update")
    val started = mutableListOf<PluginId>()
    val callback = RecordingCallback()
    val executor = PageSessionPluginUpdateAllExecutor(
      operationContextFactory = ::operationContext,
      installedPluginProvider = { id ->
        mapOf(failedInstalled.pluginId to failedInstalled, successfulInstalled.pluginId to successfulInstalled)[id]
      },
      updateStarter = { installed, _, _ ->
        started.add(installed.pluginId)
        if (installed.pluginId == failedInstalled.pluginId) error("failed")
      },
      runOnEdt = { it() },
    )

    executor.execute(
      UnifiedPluginUpdateAllRequest(1, listOf(failedUpdate, missingUpdate, successfulUpdate)),
      callback,
    )

    assertThat(started).containsExactly(failedInstalled.pluginId, successfulInstalled.pluginId)
    assertThat(callback.failures.map { it.first }).containsExactly(failedUpdate.pluginId, missingUpdate.pluginId)
  }

  @Test
  fun `executor propagates a target start cancellation`() {
    val update = plugin("cancelled", "Cancelled update")
    val cancellation = CancellationException("cancelled")
    val callback = RecordingCallback()
    val executor = PageSessionPluginUpdateAllExecutor(
      operationContextFactory = ::operationContext,
      installedPluginProvider = { update },
      updateStarter = { _, _, _ -> throw cancellation },
      runOnEdt = { it() },
    )

    assertThatThrownBy {
      executor.execute(UnifiedPluginUpdateAllRequest(1, listOf(update)), callback)
    }.isSameAs(cancellation)
    assertThat(callback.failures).isEmpty()
  }

  @Test
  fun `executor limits active updates until their operations finish`() {
    val updates = (1..20).map { index -> plugin("plugin.$index", "Update $index") }
    val installed = updates.associate { update ->
      update.pluginId to plugin(update.pluginId.idString, "Installed ${update.name}")
    }
    val started = mutableListOf<PluginUiModel>()
    val operationIds = mutableMapOf<PluginId, UUID>()
    val activeCount = AtomicInteger()
    val maximumActiveCount = AtomicInteger()
    val callback = RecordingCallback()
    val executor = PageSessionPluginUpdateAllExecutor(
      operationContextFactory = ::operationContext,
      installedPluginProvider = installed::get,
      updateStarter = { _, update, operationContext ->
        started.add(update)
        operationIds[update.pluginId] = operationContext.operationId
        val active = activeCount.incrementAndGet()
        maximumActiveCount.accumulateAndGet(active, ::maxOf)
      },
      runOnEdt = { it() },
    )

    executor.execute(UnifiedPluginUpdateAllRequest(1, updates), callback)

    assertThat(started).hasSize(3)
    updates.forEachIndexed { index, update ->
      assertThat(started).hasSizeGreaterThan(index)
      val operationId = operationIds.getValue(update.pluginId)
      executor.acceptOperationEvent(operationStarted(update, operationId))
      activeCount.decrementAndGet()
      executor.acceptOperationEvent(operationFinished(update, operationId))
    }

    assertThat(started).containsExactlyElementsOf(updates)
    assertThat(maximumActiveCount.get()).isEqualTo(3)
    assertThat(activeCount.get()).isZero()
    assertThat(callback.failures).isEmpty()
  }

  @Test
  fun `executor schedules replacement starts and drops pending updates when closed`() {
    val updates = (1..5).map { index -> plugin("plugin.$index", "Update $index") }
    val installed = updates.associateBy(PluginUiModel::pluginId)
    val scheduled = ArrayDeque<() -> Unit>()
    val started = mutableListOf<PluginUiModel>()
    val operationIds = mutableMapOf<PluginId, UUID>()
    val executor = PageSessionPluginUpdateAllExecutor(
      operationContextFactory = ::operationContext,
      installedPluginProvider = installed::get,
      updateStarter = { _, update, operationContext ->
        started.add(update)
        operationIds[update.pluginId] = operationContext.operationId
      },
      runOnEdt = scheduled::addLast,
    )

    executor.execute(UnifiedPluginUpdateAllRequest(1, updates), RecordingCallback())
    assertThat(started).isEmpty()
    scheduled.removeFirst().invoke()
    assertThat(started).containsExactlyElementsOf(updates.take(3))

    val operationId = operationIds.getValue(updates.first().pluginId)
    executor.acceptOperationEvent(operationStarted(updates.first(), operationId))
    executor.acceptOperationEvent(operationFinished(updates.first(), operationId))
    assertThat(started).hasSize(3)
    assertThat(scheduled).hasSize(1)

    executor.close()
    scheduled.removeFirst().invoke()

    assertThat(started).containsExactlyElementsOf(updates.take(3))
  }

  @Test
  fun `reset drops the active request and permits a new request`() {
    val initialUpdates = (1..5).map { index -> plugin("plugin.$index", "Update $index") }
    val replacement = plugin("replacement", "Replacement")
    val installed = (initialUpdates + replacement).associateBy(PluginUiModel::pluginId)
    val started = mutableListOf<PluginUiModel>()
    val executor = PageSessionPluginUpdateAllExecutor(
      operationContextFactory = ::operationContext,
      installedPluginProvider = installed::get,
      updateStarter = { _, update, _ -> started.add(update) },
      runOnEdt = { it() },
    )
    executor.execute(UnifiedPluginUpdateAllRequest(1, initialUpdates), RecordingCallback())

    executor.acceptOperationEvent(
      PluginModelEvent.InventoryInvalidated(PluginInventoryChangeReason.RESET, emptySet())
    )
    executor.execute(UnifiedPluginUpdateAllRequest(2, listOf(replacement)), RecordingCallback())

    assertThat(started).containsExactlyElementsOf(initialUpdates.take(3) + replacement)
  }

  @Test
  fun `executor ignores an unrelated operation for an active plugin`() {
    val updates = (1..4).map { index -> plugin("plugin.$index", "Update $index") }
    val installed = updates.associateBy(PluginUiModel::pluginId)
    val contexts = mutableMapOf<PluginId, PluginOperationContext>()
    val started = mutableListOf<PluginUiModel>()
    val executor = PageSessionPluginUpdateAllExecutor(
      operationContextFactory = { update -> operationContext(update).also { contexts[update.pluginId] = it } },
      installedPluginProvider = installed::get,
      updateStarter = { _, update, _ -> started.add(update) },
      runOnEdt = { it() },
    )
    executor.execute(UnifiedPluginUpdateAllRequest(1, updates), RecordingCallback())
    val first = updates.first()

    val unrelatedOperationId = UUID.randomUUID()
    executor.acceptOperationEvent(operationStarted(first, unrelatedOperationId))
    executor.acceptOperationEvent(operationFinished(first, unrelatedOperationId))

    assertThat(started).containsExactlyElementsOf(updates.take(3))

    val expectedOperationId = contexts.getValue(first.pluginId).operationId
    executor.acceptOperationEvent(operationStarted(first, expectedOperationId))
    executor.acceptOperationEvent(operationFinished(first, expectedOperationId))

    assertThat(started).containsExactlyElementsOf(updates)
  }

  private fun plugin(id: String, name: String): PluginUiModel {
    return PluginNodeModelBuilderFactory.createBuilder(PluginId.getId(id)).setName(name).build()
  }

  private fun operationContext(plugin: PluginUiModel): PluginOperationContext {
    return PluginOperationContext.create(plugin.pluginId, PluginSource.LOCAL, PluginOperationKind.UPDATE)
  }

  private fun operationStarted(plugin: PluginUiModel, operationId: UUID): PluginModelEvent.OperationStarted {
    return PluginModelEvent.OperationStarted(
      sessionId = "session",
      operationId = operationId,
      displayPluginId = plugin.pluginId,
      presentationModel = plugin,
      target = PluginSource.LOCAL,
      kind = PluginOperationKind.UPDATE,
    )
  }

  private fun operationFinished(plugin: PluginUiModel, operationId: UUID): PluginModelEvent.OperationFinished {
    return PluginModelEvent.OperationFinished(
      sessionId = "session",
      operationId = operationId,
      displayPluginId = plugin.pluginId,
      target = PluginSource.LOCAL,
      kind = PluginOperationKind.UPDATE,
      result = PluginOperationTerminalResult.SUCCEEDED,
    )
  }

  private class RecordingCallback : UnifiedPluginUpdateAllCallback {
    val failures = mutableListOf<Pair<PluginId, Throwable>>()

    override fun prepared(pluginId: PluginId, restartRequired: Boolean) {
      error("The executor must wait for the operation event")
    }

    override fun failed(pluginId: PluginId, cause: Throwable) {
      failures.add(pluginId to cause)
    }
  }
}
