// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeEntity
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import fleet.kernel.DurableRef
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Deferred
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface RemoteShelfApi : RemoteApi<Unit> {
  suspend fun loadChanges(projectId: ProjectId)
  suspend fun showDiffForChanges(projectId: ProjectId, changeListRpc: ChangeListRpc)
  suspend fun notifyNodeSelected(projectId: ProjectId, changeListRpc: ChangeListRpc, fromModelChange: Boolean)
  suspend fun applyTreeGrouping(projectId: ProjectId, groupingKeys: Set<String>): Deferred<UpdateStatus>
  suspend fun renameShelvedChangeList(projectId: ProjectId, changeList: DurableRef<ShelvedChangeListEntity>, newName: String)

  companion object {
    suspend fun getInstance(): RemoteShelfApi = RemoteApiProviderService.resolve(remoteApiDescriptor<RemoteShelfApi>())
  }
}

@ApiStatus.Internal
@Serializable
class ChangeListRpc(val changeList: DurableRef<ShelvedChangeListEntity>, val changes: List<DurableRef<ShelvedChangeEntity>>)

@ApiStatus.Internal
@Serializable
enum class UpdateStatus {
  OK,
  FAILED
}