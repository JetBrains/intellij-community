// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.backend.shelf

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.vcs.impl.shared.rpc.RemoteShelfApi
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.jetbrains.rhizomedb.EID
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
  override suspend fun loadChangesAsync(projectId: EID) {
    val project = getProject(projectId) ?: return
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

  private suspend fun getProject(projectId: EID): Project? {
    return serviceAsync<ProjectManager>().openProjects.firstOrNull()
  }
}