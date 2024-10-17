// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.ProjectEntity
import com.intellij.platform.project.asProject
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.vcs.impl.shared.rpc.ChangeListDto
import com.intellij.vcs.impl.shared.rpc.RemoteShelfApi
import fleet.kernel.SharedRef
import fleet.kernel.change
import fleet.kernel.shared
import com.intellij.vcs.impl.shared.rpc.UpdateStatus
import com.jetbrains.rhizomedb.EID
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

class ShelfApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<RemoteShelfApi>()) {
      BackendShelfApi()
    }
  }
}

class BackendShelfApi : RemoteShelfApi {
  override suspend fun loadChangesAsync(projectRef: SharedRef<ProjectEntity>) {
    val project = getProject(projectRef) ?: return
    ShelveChangesManager.getInstance(project).allLists.forEach {
      it.loadChangesIfNeeded(project)
    }

    val shelfTreeHolder = ShelfTreeHolder.getInstance(project)
    shelfTreeHolder.scheduleTreeUpdate()
    shelfTreeHolder.saveGroupings()
  }

  override suspend fun showDiffForChanges(projectRef: SharedRef<ProjectEntity>, changeListDto: ChangeListDto) {
    val project = getProject(projectRef) ?: return
    ShelfTreeHolder.getInstance(project).showDiff(changeListDto)
  }

  override suspend fun notifyNodeSelected(projectRef: SharedRef<ProjectEntity>, changeListDto: ChangeListDto) {
    val project = getProject(projectRef) ?: return
    ShelfTreeHolder.getInstance(project).updateSelection(changeListDto)
  }

  override suspend fun applyTreeGrouping(projectRef: SharedRef<ProjectEntity>, groupingKeys: Set<String>): Deferred<UpdateStatus> {
    val project = getProject(projectRef) ?: return CompletableDeferred(UpdateStatus.FAILED)

    ShelfTreeHolder.getInstance(project).changeGrouping(groupingKeys)
    return CompletableDeferred(UpdateStatus.OK)
  }

  private suspend fun getProject(projectRef: SharedRef<ProjectEntity>): Project? {
    return withKernel {
      change {
        shared {
          projectRef.deref().asProject()
        }
      }
    }
  }
}