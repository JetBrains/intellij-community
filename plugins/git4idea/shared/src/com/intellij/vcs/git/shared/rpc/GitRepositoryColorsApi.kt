// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.vcs.git.shared.repo.GitRepositoryColorsState
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface GitRepositoryColorsApi: RemoteApi<Unit> {
  suspend fun syncColors(projectId: ProjectId) : Flow<GitRepositoryColorsState>

  companion object {
    suspend fun getInstance() : GitRepositoryColorsApi =
      RemoteApiProviderService.resolve(remoteApiDescriptor<GitRepositoryColorsApi>())
  }
}