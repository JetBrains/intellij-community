// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf

import com.intellij.platform.project.ProjectEntity
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.vcs.impl.shared.rpc.ChangeListDto
import com.intellij.vcs.impl.shared.rpc.RemoteShelfActionsApi
import fleet.kernel.SharedRef
import fleet.rpc.remoteApiDescriptor

class ShelfActionsApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<RemoteShelfActionsApi>()) {
      BackendShelfActionsApi()
    }
  }
}

class BackendShelfActionsApi : RemoteShelfActionsApi {

  override suspend fun unshelveSilently(projectRef: SharedRef<ProjectEntity>, changeListDto: List<ChangeListDto>) {
    val project = projectRef.asProject()

    val executor = ShelfRemoteActionExecutor.getInstance(project)
    executor.unshelveSilently(changeListDto)
  }

  override suspend fun createPatchForShelvedChanges(projectRef: SharedRef<ProjectEntity>, changeListDto: List<ChangeListDto>, silentClipboard: Boolean) {
    val project = projectRef.asProject()

    val executor = ShelfRemoteActionExecutor.getInstance(project)
    executor.createPatchForShelvedChanges(changeListDto, silentClipboard)
  }
}