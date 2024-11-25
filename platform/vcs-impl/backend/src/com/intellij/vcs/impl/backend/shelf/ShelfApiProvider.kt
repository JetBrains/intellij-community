// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.ProjectEntity
import com.intellij.platform.project.asProject
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import com.intellij.vcs.impl.shared.rpc.ChangeListDto
import com.intellij.vcs.impl.shared.rpc.RemoteShelfApi
import com.intellij.vcs.impl.shared.rpc.UpdateStatus
import fleet.kernel.DurableRef
import fleet.kernel.change
import fleet.kernel.shared
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
  override suspend fun loadChangesAsync(projectRef: DurableRef<ProjectEntity>) {
    val project = projectRef.asProject()
    ShelveChangesManager.getInstance(project).allLists.forEach {
      it.loadChangesIfNeeded(project)
    }

    val shelfTreeHolder = ShelfTreeHolder.getInstance(project)
    shelfTreeHolder.scheduleTreeUpdate()
    shelfTreeHolder.saveGroupings()
  }

  override suspend fun showDiffForChanges(projectRef: DurableRef<ProjectEntity>, changeListDto: ChangeListDto) {
    val project = projectRef.asProject()
    ShelfTreeHolder.getInstance(project).showDiff(changeListDto)
  }

  override suspend fun notifyNodeSelected(projectRef: DurableRef<ProjectEntity>, changeListDto: ChangeListDto, fromModelChange: Boolean) {
    val project = projectRef.asProject()
    ShelfTreeHolder.getInstance(project).updateDiffFile(changeListDto, fromModelChange)
  }

  override suspend fun applyTreeGrouping(projectRef: DurableRef<ProjectEntity>, groupingKeys: Set<String>): Deferred<UpdateStatus> {
    val project = projectRef.asProject()

    ShelfTreeHolder.getInstance(project).changeGrouping(groupingKeys)
    return CompletableDeferred(UpdateStatus.OK)
  }

  override suspend fun renameShelvedChangeList(projectRef: DurableRef<ProjectEntity>, changeList: DurableRef<ShelvedChangeListEntity>, newName: String) {
    val project = projectRef.asProject()

    ShelfTreeHolder.getInstance(project).renameChangeList(changeList, newName)
  }

}

internal suspend fun DurableRef<ProjectEntity>.asProject(): Project {
  return withKernel {
    change {
      shared {
        deref().asProject()
      }
    }
  }
}
