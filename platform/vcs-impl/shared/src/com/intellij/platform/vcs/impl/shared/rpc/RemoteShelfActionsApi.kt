// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import fleet.kernel.DurableRef
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface RemoteShelfActionsApi : RemoteApi<Unit> {
  suspend fun unshelve(projectId: ProjectId, changeListRpc: List<ChangeListRpc>, withDialog: Boolean)
  suspend fun delete(projectId: ProjectId, selectedLists: List<DurableRef<ShelvedChangeListEntity>>, selectedChanges: List<ChangeListRpc>)
  suspend fun createPatchForShelvedChanges(projectId: ProjectId, changeListsDto: List<ChangeListRpc>, silentClipboard: Boolean)
  suspend fun showStandaloneDiff(projectId: ProjectId, changeListsDto: List<ChangeListRpc>, withLocal: Boolean)
  suspend fun importShelvesFromPatches(projectId: ProjectId)
  suspend fun navigateToSource(projectId: ProjectId, navigatables: List<ChangeListRpc>, focusEditor: Boolean)
  suspend fun restoreShelves(projectId: ProjectId, changeLists: List<DurableRef<ShelvedChangeListEntity>>)
  suspend fun createPreviewDiffSplitter(projectId: ProjectId)

  companion object {
    suspend fun getInstance(): RemoteShelfActionsApi = RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteShelfActionsApi>())
  }
}