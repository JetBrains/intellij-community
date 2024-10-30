// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.shared.rpc

import com.intellij.platform.project.ProjectEntity
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import fleet.kernel.SharedRef
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc

@Rpc
interface RemoteShelfActionsApi : RemoteApi<Unit> {
  suspend fun unshelve(projectRef: SharedRef<ProjectEntity>, changeListDto: List<ChangeListDto>, withDialog: Boolean)
  suspend fun delete(projectRef: SharedRef<ProjectEntity>, selectedLists: List<SharedRef<ShelvedChangeListEntity>>, selectedChanges: List<ChangeListDto>)
  suspend fun createPatchForShelvedChanges(projectRef: SharedRef<ProjectEntity>, changeListsDto: List<ChangeListDto>, silentClipboard: Boolean)
  suspend fun showStandaloneDiff(projectRef: SharedRef<ProjectEntity>, changeListsDto: List<ChangeListDto>, withLocal: Boolean)
  suspend fun importShelvesFromPatches(projectRef: SharedRef<ProjectEntity>)
  suspend fun navigateToSource(projectRef: SharedRef<ProjectEntity>, navigatables: List<ChangeListDto>, focusEditor: Boolean)
  suspend fun restoreShelves(projectRef: SharedRef<ProjectEntity>, changeLists: List<SharedRef<ShelvedChangeListEntity>>)
  suspend fun createPreviewDiffSplitter(projectRef: SharedRef<ProjectEntity>)
}