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
import fleet.rpc.remoteApiDescriptor

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
    withKernel {
      change {
        shared {
          ShelveChangesManager.getInstance(project).allLists.forEach {
            it.loadChangesIfNeeded(project)
          }
        }
      }
    }
    ShelfTreeHolder.getInstance(project).scheduleTreeUpdate()
  }

  override suspend fun showDiffForChanges(projectRef: SharedRef<ProjectEntity>, changeListDto: ChangeListDto) {
    val project = getProject(projectRef) ?: return
    ShelfTreeHolder.getInstance(project).showDiff(changeListDto)
  }

  override suspend fun notifyNodeSelected(projectRef: SharedRef<ProjectEntity>, changeListDto: ChangeListDto) {
    val project = getProject(projectRef) ?: return
    ShelfTreeHolder.getInstance(project).updateSelection(changeListDto)
  }

  private suspend fun getProject(projectRef: SharedRef<ProjectEntity>): Project? {
    return withKernel {
      projectRef.deref().asProject()
    }
  }
}