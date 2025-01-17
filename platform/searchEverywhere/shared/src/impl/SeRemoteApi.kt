// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.impl

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSessionEntity
import fleet.kernel.DurableRef
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface SeRemoteApi: RemoteApi<Unit> {
  suspend fun getItems(projectId: ProjectId,
                       sessionRef: DurableRef<SeSessionEntity>,
                       providerId: SeProviderId,
                       params: SeParams): Flow<SeItemData>

  suspend fun itemSelected(projectId: ProjectId,
                           sessionRef: DurableRef<SeSessionEntity>,
                           itemData: SeItemData,
                           modifiers: Int,
                           searchText: String): Boolean

  companion object {
    @JvmStatic
    suspend fun getInstance(): SeRemoteApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<SeRemoteApi>())
    }
  }
}