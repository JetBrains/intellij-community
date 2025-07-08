// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.ide.rpc.DataContextId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.impl.SeRemoteApi
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.SeLog.ITEM_EMIT
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import fleet.kernel.DurableRef
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls

@Internal
class SeFrontendItemDataProvidersFacade(private val projectId: ProjectId,
                                        val idsWithDisplayNames: Map<SeProviderId, @Nls String>,
                                        private val sessionRef: DurableRef<SeSessionEntity>,
                                        private val dataContextId: DataContextId,
                                        private val isAllTab: Boolean,
                                        val essentialProviderIds: Set<SeProviderId>) {

  val providerIds: List<SeProviderId> = idsWithDisplayNames.keys.toList()

  fun hasId(providerId: SeProviderId): Boolean = idsWithDisplayNames.containsKey(providerId)

  fun getItems(params: SeParams, disabledProviders: List<SeProviderId>): Flow<SeTransferEvent> {
    val ids = providerIds.filter { !disabledProviders.contains(it) }

    return channelFlow {
      val channel = Channel<Int>(capacity = 1, onBufferOverflow = BufferOverflow.SUSPEND)

      channel.send(DEFAULT_CHUNK_SIZE)
      var pendingCount = DEFAULT_CHUNK_SIZE

      SeRemoteApi.getInstance().getItems(projectId, sessionRef, ids, isAllTab, params, dataContextId, channel).collect { transferEvent ->
        pendingCount--
        if (pendingCount == 0) {
          pendingCount += DEFAULT_CHUNK_SIZE
          channel.send(DEFAULT_CHUNK_SIZE)
        }

        when (transferEvent) {
         is SeTransferEnd -> {
           SeLog.log(ITEM_EMIT) { "Frontend provider for ${transferEvent.providerId.value} receives transfer end event" }
         }
         is SeTransferItem -> {
           val itemData = transferEvent.itemData
           SeLog.log(ITEM_EMIT) { "Frontend provider for ${itemData.providerId.value} receives: ${itemData.uuid} - ${itemData.presentation.text}" }
         }
        }

        send(transferEvent)
      }
    }.buffer(0, onBufferOverflow = BufferOverflow.SUSPEND)
  }

  suspend fun itemSelected(
    itemData: SeItemData,
    modifiers: Int,
    searchText: String,
  ): Boolean {
    return SeRemoteApi.getInstance().itemSelected(projectId, sessionRef, itemData, modifiers, searchText, isAllTab = isAllTab)
  }

  suspend fun getSearchScopesInfos(): Map<SeProviderId, SearchScopesInfo> =
    SeRemoteApi.getInstance().getSearchScopesInfoForProviders(
      projectId, providerIds = providerIds, sessionRef = sessionRef, dataContextId = dataContextId, isAllTab = isAllTab
    )

  suspend fun getTypeVisibilityStates(index: Int): List<SeTypeVisibilityStatePresentation> =
    SeRemoteApi.getInstance().getTypeVisibilityStatesForProviders(
      index = index, projectId = projectId, providerIds = providerIds, sessionRef = sessionRef, dataContextId = dataContextId, isAllTab = isAllTab
    )

  suspend fun canBeShownInFindResults(): Boolean {
    return SeRemoteApi.getInstance().canBeShownInFindResults(
      projectId, providerIds = providerIds, sessionRef = sessionRef, dataContextId = dataContextId, isAllTab = isAllTab
    )
  }

  companion object {
    private const val DEFAULT_CHUNK_SIZE: Int = 50
  }
}