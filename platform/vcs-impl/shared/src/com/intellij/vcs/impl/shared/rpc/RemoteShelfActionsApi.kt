// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.shared.rpc

import com.intellij.platform.project.ProjectEntity
import fleet.kernel.SharedRef
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc

@Rpc
interface RemoteShelfActionsApi : RemoteApi<Unit> {
  suspend fun unshelve(projectRef: SharedRef<ProjectEntity>, changeListDto: List<ChangeListDto>, withDialog: Boolean)
  suspend fun createPatchForShelvedChanges(projectRef: SharedRef<ProjectEntity>, changeListsDto: List<ChangeListDto>, silentClipboard: Boolean)
  suspend fun compareWithLocal(projectRef: SharedRef<ProjectEntity>, changeListsDto: List<ChangeListDto>)
  suspend fun importShelvesFromPatches(projectRef: SharedRef<ProjectEntity>)
}
