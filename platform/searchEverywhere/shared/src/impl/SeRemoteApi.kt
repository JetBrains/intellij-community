// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.impl

import com.intellij.ide.rpc.DataContextId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import fleet.kernel.DurableRef
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
@Rpc
interface SeRemoteApi : RemoteApi<Unit> {
  // In the case of sending search results via RPC,
  // we can't limit the flow buffer on the backend side
  // and somehow suspend generating new search results while the UI has enough of them to present.
  // To limit the results generating, we use `requestedCountChannel`.
  //
  // The idea behind `requestedCountChannel` is the following:
  // 1. FE sends to BE ReceiveChannel,
  // 2. BE subscribes to this channel.
  // 3. FE sends "give me next 50" requests through this channel
  // 4. BE sends the next batch of items
  suspend fun getItems(
    projectId: ProjectId,
    sessionRef: DurableRef<SeSessionEntity>,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
    params: SeParams,
    dataContextId: DataContextId?,
    requestedCountChannel: ReceiveChannel<Int>,
  ): Flow<SeTransferEvent>

  suspend fun itemSelected(
    projectId: ProjectId,
    sessionRef: DurableRef<SeSessionEntity>,
    itemData: SeItemData,
    modifiers: Int,
    searchText: String,
    isAllTab: Boolean,
  ): Boolean

  /**
   * Defines if results can be shown in <i>Find</i> toolwindow.
   */
  suspend fun canBeShownInFindResults(projectId: ProjectId,
                                      sessionRef: DurableRef<SeSessionEntity>,
                                      dataContextId: DataContextId,
                                      providerIds: List<SeProviderId>,
                                      isAllTab: Boolean): Boolean

  suspend fun isShownInSeparateTab(projectId: ProjectId,
                                   sessionRef: DurableRef<SeSessionEntity>,
                                   dataContextId: DataContextId,
                                   providerId: SeProviderId): Boolean

  suspend fun openInFindToolWindow(
    projectId: ProjectId,
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId?,
    providerIds: List<SeProviderId>,
    params: SeParams,
    isAllTab: Boolean
  ): Boolean

  suspend fun getAvailableProviderIds(
    projectId: ProjectId,
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId
  ): Map<String, Set<SeProviderId>>

  suspend fun getSearchScopesInfoForProviders(
    projectId: ProjectId,
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
  ): Map<SeProviderId, SearchScopesInfo>

  suspend fun getTypeVisibilityStatesForProviders(
    index: Int,
    projectId: ProjectId,
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
  ): List<SeTypeVisibilityStatePresentation>

  suspend fun getDisplayNameForProviders(
    projectId: ProjectId,
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
  ): Map<SeProviderId, @Nls String>

  suspend fun getTextSearchOptions(projectId: ProjectId): SeTextSearchOptions?

  companion object {
    @JvmStatic
    suspend fun getInstance(): SeRemoteApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<SeRemoteApi>())
    }
  }
}