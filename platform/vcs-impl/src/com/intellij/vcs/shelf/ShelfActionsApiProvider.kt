// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.shelf

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListRpc
import com.intellij.platform.vcs.impl.shared.rpc.RemoteShelfActionsApi
import fleet.kernel.DurableRef
import fleet.rpc.remoteApiDescriptor

private class ShelfActionsApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<RemoteShelfActionsApi>()) {
      BackendShelfActionsApi()
    }
  }
}

private class BackendShelfActionsApi : RemoteShelfActionsApi {
  override suspend fun unshelve(projectId: ProjectId, changeListRpc: List<ChangeListRpc>, withDialog: Boolean) {
    getShelfRemoteActionExecutor(projectId).unshelve(changeListRpc, withDialog)
  }

  override suspend fun delete(projectId: ProjectId, selectedLists: List<DurableRef<ShelvedChangeListEntity>>, selectedChanges: List<ChangeListRpc>) {
    getShelfRemoteActionExecutor(projectId).delete(selectedLists, selectedChanges)
  }

  override suspend fun createPatchForShelvedChanges(projectId: ProjectId, changeListsDto: List<ChangeListRpc>, silentClipboard: Boolean) {
    getShelfRemoteActionExecutor(projectId).createPatchForShelvedChanges(changeListsDto, silentClipboard)
  }

  override suspend fun showStandaloneDiff(projectId: ProjectId, changeListsDto: List<ChangeListRpc>, withLocal: Boolean) {
    getShelfRemoteActionExecutor(projectId).showStandaloneDiff(changeListsDto, withLocal)
  }

  override suspend fun importShelvesFromPatches(projectId: ProjectId) {
    getShelfRemoteActionExecutor(projectId).exportPatches()
  }

  override suspend fun navigateToSource(projectId: ProjectId, navigatables: List<ChangeListRpc>, focusEditor: Boolean) {
    getShelfRemoteActionExecutor(projectId).navigateToSource(navigatables, focusEditor)
  }

  override suspend fun restoreShelves(projectId: ProjectId, changeLists: List<DurableRef<ShelvedChangeListEntity>>) {
    getShelfRemoteActionExecutor(projectId).restoreShelves(changeLists)
  }

  override suspend fun createPreviewDiffSplitter(projectId: ProjectId) {
    getShelfRemoteActionExecutor(projectId).createPreviewDiffSplitter()
  }

  private fun getShelfRemoteActionExecutor(projectId: ProjectId): ShelfRemoteActionExecutor {
    val project = projectId.findProject()

    return ShelfRemoteActionExecutor.getInstance(project)
  }
}