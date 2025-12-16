// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.rpc

import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.vcs.impl.shared.RepositoryId
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import git4idea.GitDisposable
import git4idea.GitStandardLocalBranch
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface GitOperationsApi : RemoteApi<Unit> {
  /**
   * Checkout the given [branch] in the provided [repositories] and update it from its tracked upstream.
   */
  suspend fun checkoutAndUpdate(projectId: ProjectId, repositories: List<RepositoryId>, branch: GitStandardLocalBranch): Deferred<Unit>

  companion object {
    suspend fun getInstance(): GitOperationsApi = RemoteApiProviderService.resolve(remoteApiDescriptor<GitOperationsApi>())

    fun launchRequest(project: Project, request: suspend GitOperationsApi.() -> Unit) {
      GitDisposable.getInstance(project).coroutineScope.launch {
        getInstance().request()
      }
    }
  }
}
