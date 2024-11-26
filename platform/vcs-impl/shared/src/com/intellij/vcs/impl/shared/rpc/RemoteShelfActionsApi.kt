// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.shared.rpc

import com.intellij.platform.project.ProjectEntity
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import fleet.kernel.DurableRef
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface RemoteShelfActionsApi : RemoteApi<Unit> {
  suspend fun unshelve(projectId: ProjectId, changeListDto: List<ChangeListDto>, withDialog: Boolean)
  suspend fun delete(projectId: ProjectId, selectedLists: List<DurableRef<ShelvedChangeListEntity>>, selectedChanges: List<ChangeListDto>)
  suspend fun createPatchForShelvedChanges(projectId: ProjectId, changeListsDto: List<ChangeListDto>, silentClipboard: Boolean)
  suspend fun showStandaloneDiff(projectId: ProjectId, changeListsDto: List<ChangeListDto>, withLocal: Boolean)
  suspend fun importShelvesFromPatches(projectId: ProjectId)
  suspend fun navigateToSource(projectId: ProjectId, navigatables: List<ChangeListDto>, focusEditor: Boolean)
  suspend fun restoreShelves(projectId: ProjectId, changeLists: List<DurableRef<ShelvedChangeListEntity>>)
  suspend fun createPreviewDiffSplitter(projectId: ProjectId)

  companion object {
    suspend fun getInstance(): RemoteShelfActionsApi = RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteShelfActionsApi>())
  }
}