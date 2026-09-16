// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.newui.PluginInstallationState
import com.intellij.ide.plugins.newui.PluginModelEvent
import com.intellij.ide.plugins.newui.PluginNodeModelBuilderFactory
import com.intellij.ide.plugins.newui.PluginOperationKind
import com.intellij.ide.plugins.newui.PluginOperationTerminalResult
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.extensions.PluginId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

internal class UnifiedPluginInstallingLedgerTest {
  private val ledger = UnifiedPluginInstallingLedger(SESSION_ID)

  @Test
  fun `ledger is hidden before the first accepted install`() {
    assertThat(ledger.entries).isEmpty()
    assertThat(ledger.section(PluginListModelData.EMPTY)).isNull()
  }

  @Test
  fun `install start creates one active retained entry`() {
    val model = plugin("plugin.id")
    val operationId = UUID.randomUUID()

    assertThat(ledger.accept(started(operationId, model))).isTrue()

    val entry = ledger.entries.single()
    assertThat(entry.pluginId).isEqualTo(model.pluginId)
    assertThat(entry.presentationModel).isSameAs(model)
    assertThat(entry.activeOperationIds).containsExactly(operationId)
    assertThat(entry.latestTerminalResult).isNull()
    assertThat(ledger.section(PluginListModelData.EMPTY)?.items).hasSize(1)
  }

  @Test
  fun `retry updates the retained occurrence without duplicating it`() {
    val firstModel = plugin("plugin.id", "First")
    val retryModel = plugin("plugin.id", "Retry")
    val firstOperation = UUID.randomUUID()
    val retryOperation = UUID.randomUUID()
    ledger.accept(started(firstOperation, firstModel))
    ledger.accept(finished(firstOperation, firstModel.pluginId, PluginOperationTerminalResult.FAILED))

    ledger.accept(started(retryOperation, retryModel))

    val entry = ledger.entries.single()
    assertThat(entry.presentationModel).isSameAs(retryModel)
    assertThat(entry.attempts.map { it.operationId }).containsExactly(firstOperation, retryOperation)
    assertThat(entry.activeOperationIds).containsExactly(retryOperation)
    assertThat(entry.latestTerminalResult).isEqualTo(PluginOperationTerminalResult.FAILED)
  }

  @Test
  fun `split target remains one logical attempt`() {
    val model = plugin("plugin.id")
    val operationId = UUID.randomUUID()
    ledger.accept(started(operationId, model, target = PluginSource.BOTH))

    ledger.accept(finished(operationId, model.pluginId, PluginOperationTerminalResult.SUCCEEDED, target = PluginSource.BOTH))

    val attempt = ledger.entries.single().attempts.single()
    assertThat(attempt.target).isEqualTo(PluginSource.BOTH)
    assertThat(attempt.state).isEqualTo(InstallingPluginAttemptState.Terminal(PluginOperationTerminalResult.SUCCEEDED))
  }

  @Test
  fun `all terminal results are retained`() {
    PluginOperationTerminalResult.entries.forEach { result ->
      val model = plugin("plugin.${result.name.lowercase()}")
      val operationId = UUID.randomUUID()
      ledger.accept(started(operationId, model))
      ledger.accept(finished(operationId, model.pluginId, result))
    }

    assertThat(ledger.entries.map { it.latestTerminalResult }).containsExactlyElementsOf(PluginOperationTerminalResult.entries)
    assertThat(ledger.entries).allSatisfy { entry -> assertThat(entry.activeOperationIds).isEmpty() }
  }

  @Test
  fun `installed dependencies become retained successful entries`() {
    val plugin = plugin("plugin.id")
    val firstDependency = plugin("dependency.first")
    val secondDependency = plugin("dependency.second")
    val operationId = UUID.randomUUID()
    ledger.accept(started(operationId, plugin))

    assertThat(ledger.accept(finished(
      operationId,
      plugin.pluginId,
      PluginOperationTerminalResult.SUCCEEDED,
      installedPlugins = listOf(firstDependency, secondDependency),
    ))).isTrue()

    assertThat(ledger.entries.map { it.pluginId.idString })
      .containsExactly("plugin.id", "dependency.first", "dependency.second")
    assertThat(ledger.entries.drop(1)).allSatisfy { dependency ->
      assertThat(dependency.latestTerminalResult).isEqualTo(PluginOperationTerminalResult.SUCCEEDED)
      assertThat(dependency.activeOperationIds).isEmpty()
    }
  }

  @Test
  fun `scheduled dependencies are active before operation completion`() {
    val plugin = plugin("plugin.id")
    val dependency = plugin("dependency.id")
    val operationId = UUID.randomUUID()
    ledger.accept(started(operationId, plugin))

    assertThat(ledger.accept(scheduled(operationId, plugin.pluginId, listOf(dependency)))).isTrue()

    val dependencyEntry = ledger.entries.single { it.pluginId == dependency.pluginId }
    assertThat(dependencyEntry.activeOperationIds).containsExactly(operationId)
    assertThat(ledger.section(PluginListModelData.EMPTY)!!.items.single { it.pluginId == dependency.pluginId }
                 .rowInput?.operationInProgress).isTrue()
  }

  @Test
  fun `operation failure terminates dependencies that were scheduled but not installed`() {
    val plugin = plugin("plugin.id")
    val dependency = plugin("dependency.id")
    val operationId = UUID.randomUUID()
    ledger.accept(started(operationId, plugin))
    ledger.accept(scheduled(operationId, plugin.pluginId, listOf(dependency)))

    ledger.accept(finished(operationId, plugin.pluginId, PluginOperationTerminalResult.FAILED))

    val dependencyEntry = ledger.entries.single { it.pluginId == dependency.pluginId }
    assertThat(dependencyEntry.activeOperationIds).isEmpty()
    assertThat(dependencyEntry.latestTerminalResult).isEqualTo(PluginOperationTerminalResult.FAILED)
    assertThat(ledger.section(PluginListModelData.EMPTY)!!.items.single { it.pluginId == dependency.pluginId }
                 .rowInput?.operationInProgress).isFalse()
  }

  @Test
  fun `an update and its installed dependencies are retained`() {
    val update = plugin("update.id")
    val dependency = plugin("dependency.id")
    val operationId = UUID.randomUUID()

    assertThat(ledger.accept(started(operationId, update, kind = PluginOperationKind.UPDATE))).isTrue()
    assertThat(ledger.accept(finished(
      operationId,
      update.pluginId,
      PluginOperationTerminalResult.SUCCEEDED,
      kind = PluginOperationKind.UPDATE,
      installedPlugins = listOf(dependency),
    ))).isTrue()

    assertThat(ledger.entries.map { it.pluginId.idString }).containsExactly("update.id", "dependency.id")
    assertThat(ledger.entries.first().latestTerminalResult).isEqualTo(PluginOperationTerminalResult.SUCCEEDED)
  }

  @Test
  fun `an update and its scheduled dependencies are active`() {
    val update = plugin("update.id")
    val dependency = plugin("dependency.id")
    val operationId = UUID.randomUUID()

    ledger.accept(started(operationId, update, kind = PluginOperationKind.UPDATE))

    assertThat(ledger.accept(scheduled(
      operationId, update.pluginId, listOf(dependency), kind = PluginOperationKind.UPDATE,
    ))).isTrue()
    assertThat(ledger.entries.map { it.pluginId.idString }).containsExactly("update.id", "dependency.id")
    assertThat(ledger.entries).allSatisfy { entry ->
      assertThat(entry.activeOperationIds).containsExactly(operationId)
    }
  }

  @Test
  fun `other sessions and unknown completions are ignored`() {
    val model = plugin("plugin.id")
    val operationId = UUID.randomUUID()

    assertThat(ledger.accept(started(operationId, model, sessionId = "other"))).isFalse()
    assertThat(ledger.accept(finished(operationId, model.pluginId, PluginOperationTerminalResult.SUCCEEDED))).isFalse()
    assertThat(ledger.entries).isEmpty()
  }

  @Test
  fun `section projection combines operation model with current list facts`() {
    val model = plugin("plugin.id")
    val installedModel = plugin("plugin.id", "Installed")
    ledger.accept(started(UUID.randomUUID(), model))
    val installationState = PluginInstallationState(true)
    val listData = PluginListModelData(
      installedModels = mapOf(model.pluginId to installedModel),
      errors = emptyMap(),
      installationStates = mapOf(model.pluginId to installationState),
    )

    val item = ledger.section(listData)!!.items.single()

    assertThat(item.modelHandle?.model).isSameAs(model)
    assertThat(item.rowInput?.installedPlugin).isSameAs(installedModel)
    assertThat(item.rowInput?.installationState).isSameAs(installationState)
  }

  private fun started(
    operationId: UUID,
    model: PluginUiModel,
    sessionId: String = SESSION_ID,
    target: PluginSource = PluginSource.LOCAL,
    kind: PluginOperationKind = PluginOperationKind.INSTALL,
  ): PluginModelEvent.OperationStarted {
    return PluginModelEvent.OperationStarted(sessionId, operationId, model.pluginId, model, target, kind)
  }

  private fun finished(
    operationId: UUID,
    pluginId: PluginId,
    result: PluginOperationTerminalResult,
    target: PluginSource = PluginSource.LOCAL,
    kind: PluginOperationKind = PluginOperationKind.INSTALL,
    installedPlugins: List<PluginUiModel> = emptyList(),
  ): PluginModelEvent.OperationFinished {
    return PluginModelEvent.OperationFinished(
      SESSION_ID, operationId, pluginId, target, kind, result, installedPlugins
    )
  }

  private fun scheduled(
    operationId: UUID,
    pluginId: PluginId,
    dependencies: List<PluginUiModel>,
    kind: PluginOperationKind = PluginOperationKind.INSTALL,
  ): PluginModelEvent.OperationDependenciesScheduled {
    return PluginModelEvent.OperationDependenciesScheduled(
      SESSION_ID, operationId, pluginId, dependencies, PluginSource.LOCAL, kind,
    )
  }

  private fun plugin(id: String, name: String = id): PluginUiModel {
    val pluginId = PluginId.getId(id)
    return PluginNodeModelBuilderFactory.createBuilder(pluginId).setName(name).build()
  }

  private companion object {
    const val SESSION_ID: String = "session"
  }
}
