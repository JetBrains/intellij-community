// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.vcs.impl.shared.commit.EditedCommitPresentation
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

/**
 * API to interact with the changes view presentation on the backend.
 * Note that there should be a single instance of a changes view model per project.
 */
@Rpc
@ApiStatus.Internal
interface ChangesViewApi : RemoteApi<Unit> {
  /**
   * @return backend events that should be applied to the frontend changes view
   */
  suspend fun getBackendChangesViewEvents(projectId: ProjectId): Flow<BackendChangesViewEvent>

  suspend fun canExcludeFromCommit(projectId: ProjectId): Flow<Boolean>

  /**
   * Notifies when refresh request with [BackendChangesViewEvent.RefreshRequested.refreshCounter] is performed.
   */
  suspend fun notifyRefreshPerformed(projectId: ProjectId, refreshCounter: Int)

  suspend fun showResolveConflictsDialog(projectId: ProjectId, changeIds: List<ChangeId>)

  /**
   * Emits `true` if Commit toolwindow is enabled, `false` if commit tab is shown inside VCS toolwindow.
   * Initially emits the current state.
   *
   * @see [com.intellij.vcs.commit.CommitMode.useCommitToolWindow]
   */
  suspend fun isCommitToolWindowEnabled(projectId: ProjectId): Flow<Boolean>

  suspend fun synchronizeInclusion(projectId: ProjectId)

  suspend fun getEditedCommit(projectId: ProjectId): Flow<EditedCommitPresentation?>

  companion object {
    suspend fun getInstance(): ChangesViewApi = RemoteApiProviderService.resolve(remoteApiDescriptor<ChangesViewApi>())
  }
}
