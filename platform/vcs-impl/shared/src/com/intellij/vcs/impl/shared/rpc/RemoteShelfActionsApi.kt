// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.shared.rpc

import com.intellij.platform.project.ProjectEntity
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import fleet.kernel.DurableRef
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface RemoteShelfActionsApi : RemoteApi<Unit> {
  suspend fun unshelve(projectRef: DurableRef<ProjectEntity>, changeListDto: List<ChangeListDto>, withDialog: Boolean)
  suspend fun delete(projectRef: DurableRef<ProjectEntity>, selectedLists: List<DurableRef<ShelvedChangeListEntity>>, selectedChanges: List<ChangeListDto>)
  suspend fun createPatchForShelvedChanges(projectRef: DurableRef<ProjectEntity>, changeListsDto: List<ChangeListDto>, silentClipboard: Boolean)
  suspend fun showStandaloneDiff(projectRef: DurableRef<ProjectEntity>, changeListsDto: List<ChangeListDto>, withLocal: Boolean)
  suspend fun importShelvesFromPatches(projectRef: DurableRef<ProjectEntity>)
  suspend fun navigateToSource(projectRef: DurableRef<ProjectEntity>, navigatables: List<ChangeListDto>, focusEditor: Boolean)
  suspend fun restoreShelves(projectRef: DurableRef<ProjectEntity>, changeLists: List<DurableRef<ShelvedChangeListEntity>>)
  suspend fun createPreviewDiffSplitter(projectRef: DurableRef<ProjectEntity>)
}