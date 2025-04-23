// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.ide.rpc.DataContextId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.impl.SeRemoteApi
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import fleet.kernel.DurableRef
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class SeRemoteApiImpl: SeRemoteApi {
  override suspend fun itemSelected(projectId: ProjectId,
                                    sessionRef: DurableRef<SeSessionEntity>,
                                    itemData: SeItemData,
                                    modifiers: Int,
                                    searchText: String): Boolean {
    return SeBackendService.getInstance(projectId.findProject()).itemSelected(sessionRef, itemData, modifiers, searchText)
  }

  override suspend fun canBeShownInFindResults(
    projectId: ProjectId,
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerId: SeProviderId,
  ): Boolean {
    return SeBackendService.getInstance(projectId.findProject()).canBeShownInFindResults(sessionRef, dataContextId, providerId)
  }

  override suspend fun getItems(
    projectId: ProjectId,
    sessionRef: DurableRef<SeSessionEntity>,
    providerId: SeProviderId,
    params: SeParams,
    dataContextId: DataContextId?,
    requestedCountChannel: ReceiveChannel<Int>,
  ): Flow<SeItemData> {
    return SeBackendService.getInstance(projectId.findProject())
      .getItems(sessionRef, providerId, params, dataContextId, requestedCountChannel)
  }

  override suspend fun getAvailableProviderIds(): List<SeProviderId> {
    return SeItemsProviderFactory.EP_NAME.extensionList.map { SeProviderId(it.id) }
  }

  override suspend fun getSearchScopesInfoForProvider(projectId: ProjectId,
                                                      sessionRef: DurableRef<SeSessionEntity>,
                                                      dataContextId: DataContextId,
                                                      providerId: SeProviderId): SeSearchScopesInfo? {
    return SeBackendService.getInstance(projectId.findProject()).getSearchScopesInfoForProvider(sessionRef, dataContextId, providerId)
  }

  override suspend fun getTypeVisibilityStatesForProvider(projectId: ProjectId,
                                                          sessionRef: DurableRef<SeSessionEntity>,
                                                          dataContextId: DataContextId,
                                                          providerId: SeProviderId): List<SeTypeVisibilityStatePresentation>? {
    return SeBackendService.getInstance(projectId.findProject()).getTypeVisibilityStatesForProvider(sessionRef, dataContextId, providerId)
  }

  override suspend fun getDisplayNameForProviders(
    projectId: ProjectId,
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
  ): Map<SeProviderId, @Nls String> {
    return SeBackendService.getInstance(projectId.findProject()).getDisplayNameForProvider(sessionRef, dataContextId, providerIds)
  }
}
