// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface SearchEverywhereRemoteApi: RemoteApi<Unit> {
  suspend fun getProviderIds(projectId: ProjectId): List<SearchEverywhereProviderId>

  suspend fun getItems(params: SearchEverywhereParams,
                       providerId: SearchEverywhereProviderId,
                       projectId: ProjectId): Flow<SearchEverywhereItemData>

  suspend fun itemSelected(itemDate: SearchEverywhereItemData, projectId: ProjectId)

  companion object {
    @JvmStatic
    suspend fun getInstance(): SearchEverywhereRemoteApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<SearchEverywhereRemoteApi>())
    }
  }
}