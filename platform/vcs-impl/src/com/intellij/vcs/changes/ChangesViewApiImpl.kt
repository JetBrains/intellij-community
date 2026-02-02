// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UiWithModelAccess
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ChangesViewWorkflowManager
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.openapi.vcs.changes.InclusionModel
import com.intellij.platform.project.ProjectId
import com.intellij.platform.vcs.impl.shared.commit.EditedCommitPresentation
import com.intellij.platform.vcs.impl.shared.rpc.BackendChangesViewEvent
import com.intellij.platform.vcs.impl.shared.rpc.ChangeId
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewApi
import com.intellij.platform.vcs.impl.shared.rpc.InclusionDto
import com.intellij.vcs.changes.viewModel.getRpcChangesView
import com.intellij.vcs.commit.CommitModeManager
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.getProjectScoped
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.projectScoped
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.projectScopedCallbackFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class ChangesViewApiImpl : ChangesViewApi {
  override suspend fun getBackendChangesViewEvents(projectId: ProjectId): Flow<BackendChangesViewEvent> =
    projectScopedCallbackFlow(projectId) { project, _ ->
      val changesViewModel = project.getRpcChangesView()
      val inclusion = MutableSharedFlow<Set<Any>>(replay = 1)
      launch {
        inclusion.map { includedItems ->
          includedItems.map { item -> InclusionDto.toDto(item) }
        }.distinctUntilChanged().collect { send(BackendChangesViewEvent.InclusionChanged(it)) }
      }

      launch {
        changesViewModel.inclusionModel.collectLatest { newModel ->
          if (newModel != null) {
            LOG.trace { "New inclusion model is set" }
            // Terminates when the new model is assigned
            handleNewInclusionModel(newModel, inclusion::emit)
          }
          else {
            LOG.trace { "Inclusion model is null" }
            inclusion.emit(emptySet())
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

  override suspend fun canExcludeFromCommit(projectId: ProjectId): Flow<Boolean> = getProjectScoped(projectId) { project ->
    project.serviceAsync<ChangesViewWorkflowManager>().allowExcludeFromCommit
  } ?: flowOf(false)

  override suspend fun showResolveConflictsDialog(projectId: ProjectId, changeIds: List<ChangeId>) = projectScoped(projectId) { project ->
    LOG.trace { "Showing resolve conflicts dialog for ${changeIds.size} changes" }
    val changes = ChangesViewChangeIdProvider.getInstance(project).getChangeListChanges(changeIds)
    withContext(Dispatchers.EDT) {
      AbstractVcsHelper.getInstance(project).showMergeDialog(ChangesUtil.iterateFiles(changes).toList())
    }
  }

  override suspend fun isCommitToolWindowEnabled(projectId: ProjectId): Flow<Boolean> = getProjectScoped(projectId) { project ->
    project.serviceAsync<CommitModeManager>().commitModeState.map { it.isCommitTwEnabled }
  } ?: flowOf(false)

  override suspend fun synchronizeInclusion(projectId: ProjectId) = projectScoped(projectId) { project ->
    val changeListManager = ChangeListManager.getInstance(project)
    val changeLists = changeListManager.getChangeLists()
    val unversionedFiles = changeListManager.unversionedFilesPaths
    withContext(Dispatchers.UiWithModelAccess) {
      ChangesViewWorkflowManager.getInstance(project).commitWorkflowHandler?.synchronizeInclusion(changeLists, unversionedFiles)
    }
  }

  override suspend fun getEditedCommit(projectId: ProjectId): Flow<EditedCommitPresentation?> = getProjectScoped(projectId) { project ->
    project.serviceAsync<ChangesViewWorkflowManager>().editedCommit
  } ?: flowOf(null)

  /**
   * Subscribes [model] to report the current inclusion state on its update. Also, reports the initial inclusion state.
   *
   * @see [InclusionListener.inclusionChanged]
   */
  private suspend fun handleNewInclusionModel(model: InclusionModel, onInclusionUpdate: suspend (Set<Any>) -> Unit): Nothing {
    coroutineScope {
      val listener = object : InclusionListener {
        override fun inclusionChanged() {
          val newInclusion = model.getInclusion()
          LOG.trace { "Inclusion changed - ${newInclusion.size} items" }
          launch { onInclusionUpdate(newInclusion) }
        }
      }
      model.addInclusionListener(listener)
      onInclusionUpdate(model.getInclusion())
      LOG.trace { "Initial value sent" }
      try {
        awaitCancellation()
      }
      finally {
        model.removeInclusionListener(listener)
      }
    }
  }

  companion object {
    private val LOG = logger<ChangesViewApiImpl>()
  }
}