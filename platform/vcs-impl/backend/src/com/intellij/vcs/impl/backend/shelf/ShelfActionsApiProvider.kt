// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf

import com.intellij.platform.project.ProjectEntity
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import com.intellij.vcs.impl.shared.rpc.ChangeListDto
import com.intellij.vcs.impl.shared.rpc.RemoteShelfActionsApi
import fleet.kernel.SharedRef
import fleet.rpc.remoteApiDescriptor

class ShelfActionsApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<RemoteShelfActionsApi>()) {
      BackendShelfActionsApi()
    }
  }
}

class BackendShelfActionsApi : RemoteShelfActionsApi {

  override suspend fun unshelve(projectRef: SharedRef<ProjectEntity>, changeListDto: List<ChangeListDto>, withDialog: Boolean) {
    getShelfRemoteActionExecutor(projectRef).unshelve(changeListDto, withDialog)
  }

  override suspend fun createPatchForShelvedChanges(projectRef: SharedRef<ProjectEntity>, changeListDto: List<ChangeListDto>, silentClipboard: Boolean) {
    getShelfRemoteActionExecutor(projectRef).createPatchForShelvedChanges(changeListDto, silentClipboard)
  }

  override suspend fun showStandaloneDiff(projectRef: SharedRef<ProjectEntity>, changeListsDto: List<ChangeListDto>, withLocal: Boolean) {
    getShelfRemoteActionExecutor(projectRef).showStandaloneDiff(changeListsDto, withLocal)
  }

  override suspend fun importShelvesFromPatches(projectRef: SharedRef<ProjectEntity>) {
    getShelfRemoteActionExecutor(projectRef).exportPatches()
  }

  private suspend fun getShelfRemoteActionExecutor(projectRef: SharedRef<ProjectEntity>): ShelfRemoteActionExecutor {
    val project = projectRef.asProject()

    return ShelfRemoteActionExecutor.getInstance(project)
  }
}