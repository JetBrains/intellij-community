// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.openapi.vcs.changes.InclusionModel
import com.intellij.platform.project.ProjectId
import com.intellij.platform.vcs.impl.shared.rpc.BackendChangesViewEvent
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewApi
import com.intellij.platform.vcs.impl.shared.rpc.InclusionDto
import com.intellij.vcs.changes.viewModel.getRpcChangesView
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.projectScoped
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.projectScopedCallbackFlow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class ChangesViewApiImpl : ChangesViewApi {
  override suspend fun getBackendChangesViewEvents(projectId: ProjectId): Flow<BackendChangesViewEvent> =
    projectScopedCallbackFlow(projectId) { project, _ ->
      val changesViewModel = project.getRpcChangesView()

      launch {
        changesViewModel.inclusionModel.collectLatest { newModel ->
          if (newModel != null) {
            LOG.trace { "New inclusion model is set" }
            handleNewInclusionModel(newModel, channel)
          }
          else {
            LOG.trace { "Inclusion model is null" }
          }
        }
      }
      launch {
        changesViewModel.eventsForFrontend.collect {
          LOG.trace { "Sending event from backend: $it" }
          send(it)
        }
      }
    }

  override suspend fun notifyRefreshPerformed(projectId: ProjectId, refreshCounter: Int) = projectScoped(projectId) { project ->
    LOG.trace { "Refresh performed ($refreshCounter)" }
    project.getRpcChangesView().refreshPerformed(refreshCounter)
  }

  private suspend fun handleNewInclusionModel(newModel: InclusionModel, channel: SendChannel<BackendChangesViewEvent>): Nothing {
    coroutineScope {
      val listener = object : InclusionListener {
        override fun inclusionChanged() {
          val newInclusion = newModel.getInclusion().map { InclusionDto.toDto(it) }
          LOG.trace { "Inclusion changed - ${newInclusion.size} items" }
          launch {
            channel.send(BackendChangesViewEvent.InclusionChanged(newInclusion))
          }
        }
      }
      newModel.addInclusionListener(listener)
      channel.send(BackendChangesViewEvent.InclusionChanged(newModel.getInclusion().map { InclusionDto.toDto(it) }))
      LOG.trace { "Initial value sent" }
      try {
        awaitCancellation()
      }
      finally {
        newModel.removeInclusionListener(listener)
      }
    }
  }

  companion object {
    private val LOG = logger<ChangesViewApiImpl>()
  }
}