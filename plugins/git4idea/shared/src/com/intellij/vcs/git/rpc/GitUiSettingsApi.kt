// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface GitUiSettingsApi: RemoteApi<Unit> {
  suspend fun setGroupingByPrefix(projectId: ProjectId, groupByPrefix: Boolean)

  suspend fun initBranchSyncPolicyIfNotInitialized(projectId: ProjectId)

  suspend fun setShowTags(projectId: ProjectId, showTags: Boolean)

  companion object {
    suspend fun getInstance(): GitUiSettingsApi = RemoteApiProviderService.resolve(remoteApiDescriptor<GitUiSettingsApi>())
  }
}