// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.shelf

import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.platform.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import com.intellij.platform.vcs.impl.shared.rpc.ChangeListRpc
import com.intellij.platform.vcs.impl.shared.rpc.RemoteShelfApi
import com.intellij.platform.vcs.impl.shared.rpc.UpdateStatus
import fleet.kernel.DurableRef
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ShelfApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<RemoteShelfApi>()) {
      BackendShelfApi()
    }
  }
}

internal class BackendShelfApi : RemoteShelfApi {
  override suspend fun loadChanges(projectId: ProjectId) {
    val project = projectId.findProject()
    ShelveChangesManager.getInstance(project).allLists.forEach {
      it.loadChangesIfNeeded(project)
    }

    val shelfTreeHolder = ShelfTreeHolder.getInstance(project)
    val onUpdateFinished = CompletableDeferred<Unit>()
    shelfTreeHolder.scheduleTreeUpdate { onUpdateFinished.complete(Unit) }
    shelfTreeHolder.saveGroupings()
    onUpdateFinished.await()
  }

  override suspend fun showDiffForChanges(projectId: ProjectId, changeListRpc: ChangeListRpc) {
    val project = projectId.findProject()
    ShelfTreeHolder.getInstance(project).showDiff(changeListRpc)
  }

  override suspend fun notifyNodeSelected(projectId: ProjectId, changeListRpc: ChangeListRpc, fromModelChange: Boolean) {
    val project = projectId.findProject()
    ShelfTreeHolder.getInstance(project).updateDiffFile(changeListRpc, fromModelChange)
  }

  override suspend fun applyTreeGrouping(projectId: ProjectId, groupingKeys: Set<String>): Deferred<UpdateStatus> {
    val project = projectId.findProject()
    ShelfTreeHolder.getInstance(project).changeGrouping(groupingKeys)
    return CompletableDeferred(UpdateStatus.OK)
  }

  override suspend fun renameShelvedChangeList(projectId: ProjectId, changeList: DurableRef<ShelvedChangeListEntity>, newName: String) {
    val project = projectId.findProject()

    ShelfTreeHolder.getInstance(project).renameChangeList(changeList, newName)
  }

}
