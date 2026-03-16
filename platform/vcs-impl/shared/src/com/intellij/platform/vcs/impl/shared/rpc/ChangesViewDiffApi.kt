// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import com.intellij.openapi.vcs.changes.ChangesViewDiffAction
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface ChangesViewDiffApi : RemoteApi<Unit> {
  suspend fun performDiffAction(projectId: ProjectId, action: ChangesViewDiffAction)

  suspend fun notifySelectionUpdated(projectId: ProjectId, selection: ChangesViewDiffableSelection?)

  companion object {
    suspend fun getInstance(): ChangesViewDiffApi = RemoteApiProviderService.resolve(remoteApiDescriptor<ChangesViewDiffApi>())
  }
}
