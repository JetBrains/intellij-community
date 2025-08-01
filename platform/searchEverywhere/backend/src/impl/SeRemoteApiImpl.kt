// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.find.FindManager
import com.intellij.ide.rpc.DataContextId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.impl.SeRemoteApi
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import fleet.kernel.DurableRef
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class SeRemoteApiImpl: SeRemoteApi {
  override suspend fun itemSelected(projectId: ProjectId,
                                    sessionRef: DurableRef<SeSessionEntity>,
                                    itemData: SeItemData,
                                    modifiers: Int,
                                    searchText: String,
                                    isAllTab: Boolean): Boolean {
    val project = projectId.findProjectOrNull() ?: return false
    return SeBackendService.getInstance(project).itemSelected(sessionRef, itemData, modifiers, searchText, isAllTab)
  }

  override suspend fun canBeShownInFindResults(
    projectId: ProjectId,
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
  ): Boolean {
    val project = projectId.findProjectOrNull() ?: return false
    return SeBackendService.getInstance(project).canBeShownInFindResults(sessionRef, dataContextId, providerIds, isAllTab)
  }

  override suspend fun openInFindToolWindow(
    projectId: ProjectId,
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId?,
    providerIds: List<SeProviderId>,
    params: SeParams,
    isAllTab: Boolean
  ): Boolean {
    val project = projectId.findProjectOrNull() ?: return false
    return SeBackendService.getInstance(project)
      .openInFindToolWindow(projectId, sessionRef, dataContextId, providerIds, params, isAllTab)
  }

  override suspend fun isShownInSeparateTab(projectId: ProjectId, sessionRef: DurableRef<SeSessionEntity>, dataContextId: DataContextId, providerId: SeProviderId): Boolean {
    val project = projectId.findProjectOrNull() ?: return false
    return SeBackendService.getInstance(project).isShownInSeparateTab(sessionRef, dataContextId, providerId)
  }

  override suspend fun getItems(
    projectId: ProjectId,
    sessionRef: DurableRef<SeSessionEntity>,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
    params: SeParams,
    dataContextId: DataContextId?,
    requestedCountChannel: ReceiveChannel<Int>,
  ): Flow<SeTransferEvent> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return SeBackendService.getInstance(project)
      .getItems(sessionRef, providerIds, isAllTab, params, dataContextId, requestedCountChannel)
  }

  override suspend fun getAvailableProviderIds(
    projectId: ProjectId,
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId
  ) : Map<String, Set<SeProviderId>> {
    val project = projectId.findProjectOrNull() ?: return emptyMap()
    return SeBackendService.getInstance(project).getAvailableProviderIds(sessionRef, dataContextId)
  }

  override suspend fun getSearchScopesInfoForProviders(
    projectId: ProjectId,
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
  ): Map<SeProviderId, SearchScopesInfo> {
    val project = projectId.findProjectOrNull() ?: return emptyMap()
    return SeBackendService.getInstance(project).getSearchScopesInfoForProviders(sessionRef, dataContextId, providerIds, isAllTab)
  }

  override suspend fun getTypeVisibilityStatesForProviders(
    index: Int,
    projectId: ProjectId,
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
  ): List<SeTypeVisibilityStatePresentation> {
    val project = projectId.findProjectOrNull() ?: return emptyList()
    return SeBackendService.getInstance(project).getTypeVisibilityStatesForProviders(index, sessionRef, dataContextId, providerIds, isAllTab)
  }

  override suspend fun getDisplayNameForProviders(
    projectId: ProjectId,
    sessionRef: DurableRef<SeSessionEntity>,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
  ): Map<SeProviderId, @Nls String> {
    val project = projectId.findProjectOrNull() ?: return emptyMap()
    return SeBackendService.getInstance(project).getDisplayNameForProvider(sessionRef, dataContextId, providerIds)
  }

  override suspend fun getTextSearchOptions(projectId: ProjectId): SeTextSearchOptions? {
    val project = projectId.findProjectOrNull() ?: return null
    val findModel = FindManager.getInstance(project).findInProjectModel
    return SeTextSearchOptions(findModel.isCaseSensitive, findModel.isWholeWordsOnly, findModel.isRegularExpressions)
  }
}
