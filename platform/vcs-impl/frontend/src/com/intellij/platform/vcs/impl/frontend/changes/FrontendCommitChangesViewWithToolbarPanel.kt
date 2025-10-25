// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.CommitChangesViewWithToolbarPanel
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.LocalChangesListView
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.shared.changes.ChangeListsViewModel
import com.intellij.platform.vcs.impl.shared.rpc.BackendChangesViewEvent
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewApi
import fleet.rpc.client.durable
import fleet.util.logging.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

internal class FrontendCommitChangesViewWithToolbarPanel(
  changesView: ChangesListView,
  cs: CoroutineScope,
  val inclusionModel: ChangesViewDelegatingInclusionModel,
) : CommitChangesViewWithToolbarPanel(changesView, cs) {
  init {
    changesView.setInclusionModel(inclusionModel)
    ChangeListsViewModel.getInstance(project).changeLists.onEach { scheduleRefresh() }.launchIn(cs)
    cs.launch {
      subscribeToBackendEvents()
    }
  }

  override fun getModelData(): ModelData {
    val changeLists = ChangeListsViewModel.getInstance(project).changeLists.value
    return ModelData(changeLists.lists, emptyList(), emptyList()) { true }
  }

  override fun synchronizeInclusion(changeLists: List<LocalChangeList>, unversionedFiles: List<FilePath>) {
  }

  private suspend fun subscribeToBackendEvents() {
    durable {
      ChangesViewApi.getInstance().getBackendChangesViewEvents(project.projectId()).collect { event ->
        handleBackendEvent(event)
      }
    }
  }

  private suspend fun handleBackendEvent(event: BackendChangesViewEvent) {
    LOG.debug { "Handling backend event: $event" }
    when (event) {
      is BackendChangesViewEvent.InclusionChanged -> inclusionModel.applyBackendState(event.inclusionState)
      is BackendChangesViewEvent.RefreshRequested -> scheduleRefresh(event.withDelay, event.refreshCounter)
      is BackendChangesViewEvent.ToggleCheckboxes -> changesView.setShowCheckboxes(event.showCheckboxes)
    }
  }

  private fun scheduleRefresh(withDelay: Boolean, refreshCounter: Int) {
    scheduleRefresh(withDelay) {
      cs.launch {
        LOG.debug { "Changes view refreshed ($refreshCounter)" }
        ChangesViewApi.getInstance().notifyRefreshPerformed(project.projectId(), refreshCounter)
      }
    }
  }

  companion object {
    private val LOG = logger<FrontendCommitChangesViewWithToolbarPanel>()

    fun create(project: Project, cs: CoroutineScope): FrontendCommitChangesViewWithToolbarPanel {
      val tree = LocalChangesListView(project)
      val inclusionModel = ChangesViewDelegatingInclusionModel(project, cs)

      return FrontendCommitChangesViewWithToolbarPanel(tree, cs, inclusionModel)
    }
  }
}