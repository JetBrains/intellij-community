// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.impl

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.searchEverywhere.SearchEverywhereItemData
import com.intellij.platform.searchEverywhere.SearchEverywhereParams
import com.intellij.platform.searchEverywhere.SearchEverywhereProviderId
import com.jetbrains.rhizomedb.EID
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface SearchEverywhereRemoteApi: RemoteApi<Unit> {
  suspend fun getItems(projectId: ProjectId,
                       sessionId: EID,
                       providerId: SearchEverywhereProviderId,
                       params: SearchEverywhereParams): Flow<SearchEverywhereItemData>

  suspend fun itemSelected(projectId: ProjectId, itemId: EID)

  companion object {
    @JvmStatic
    suspend fun getInstance(): SearchEverywhereRemoteApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<SearchEverywhereRemoteApi>())
    }
  }
}