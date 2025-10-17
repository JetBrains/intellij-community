// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.ide.rpc.DataContextId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.impl.SeRemoteApi
import com.intellij.platform.searchEverywhere.providers.SeSortedProviderIds
import com.intellij.platform.searchEverywhere.providers.target.SeTypeVisibilityStatePresentation
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class SeRemoteApiImpl : SeRemoteApi {
  override suspend fun itemSelected(
    projectId: ProjectId,
    session: SeSession,
    itemData: SeItemData,
    modifiers: Int,
    searchText: String,
    isAllTab: Boolean,
  ): Boolean {
    val project = projectId.findProjectOrNull() ?: return false
    return SeBackendService.getInstance(project).itemSelected(session, itemData, modifiers, searchText, isAllTab)
  }

  override suspend fun canBeShownInFindResults(
    projectId: ProjectId,
    session: SeSession,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
  ): Boolean {
    val project = projectId.findProjectOrNull() ?: return false
    return SeBackendService.getInstance(project).canBeShownInFindResults(session, dataContextId, providerIds, isAllTab)
  }

  override suspend fun openInFindToolWindow(
    projectId: ProjectId,
    session: SeSession,
    dataContextId: DataContextId?,
    providerIds: List<SeProviderId>,
    params: SeParams,
    isAllTab: Boolean,
  ): Boolean {
    val project = projectId.findProjectOrNull() ?: return false
    return SeBackendService.getInstance(project)
      .openInFindToolWindow(projectId, session, dataContextId, providerIds, params, isAllTab)
  }

  override suspend fun isShownInSeparateTab(projectId: ProjectId, session: SeSession, dataContextId: DataContextId, providerId: SeProviderId): Boolean {
    val project = projectId.findProjectOrNull() ?: return false
    return SeBackendService.getInstance(project).isShownInSeparateTab(session, dataContextId, providerId)
  }

  override suspend fun getItems(
    projectId: ProjectId,
    session: SeSession,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
    params: SeParams,
    dataContextId: DataContextId?,
    requestedCountChannel: ReceiveChannel<Int>,
  ): Flow<SeTransferEvent> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    return SeBackendService.getInstance(project)
      .getItems(session, providerIds, isAllTab, params, dataContextId, requestedCountChannel)
  }

  override suspend fun getAvailableProviderIds(
    projectId: ProjectId,
    session: SeSession,
    dataContextId: DataContextId,
  ): SeSortedProviderIds? {
    val project = projectId.findProjectOrNull() ?: return null
    return SeBackendService.getInstance(project).getAvailableProviderIds(session, dataContextId)
  }

  override suspend fun getSearchScopesInfoForProviders(
    projectId: ProjectId,
    session: SeSession,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
  ): Map<SeProviderId, SearchScopesInfo> {
    val project = projectId.findProjectOrNull() ?: return emptyMap()
    return SeBackendService.getInstance(project).getSearchScopesInfoForProviders(session, dataContextId, providerIds, isAllTab)
  }

  override suspend fun getTypeVisibilityStatesForProviders(
    index: Int,
    projectId: ProjectId,
    session: SeSession,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
  ): List<SeTypeVisibilityStatePresentation> {
    val project = projectId.findProjectOrNull() ?: return emptyList()
    return SeBackendService.getInstance(project).getTypeVisibilityStatesForProviders(index, session, dataContextId, providerIds, isAllTab)
  }

  override suspend fun getDisplayNameForProviders(
    projectId: ProjectId,
    session: SeSession,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
  ): Map<SeProviderId, @Nls String> {
    val project = projectId.findProjectOrNull() ?: return emptyMap()
    return SeBackendService.getInstance(project).getDisplayNameForProvider(session, dataContextId, providerIds)
  }

  override suspend fun getUpdatedPresentation(projectId: ProjectId, item: SeItemData): SeItemPresentation? {
    val project = projectId.findProjectOrNull() ?: return null
    return SeBackendService.getInstance(project).getUpdatedPresentation(item)
  }

  override suspend fun performExtendedAction(
    projectId: ProjectId,
    session: SeSession,
    itemData: SeItemData,
    isAllTab: Boolean,
  ): Boolean {
    val project = projectId.findProjectOrNull() ?: return false
    return SeBackendService.getInstance(project).performExtendedAction(session, itemData, isAllTab)
  }

  override suspend fun getPreviewInfo(
    projectId: ProjectId,
    session: SeSession,
    itemData: SeItemData,
    isAllTab: Boolean,
  ): SePreviewInfo? {
    val project = projectId.findProjectOrNull() ?: return null
    return SeBackendService.getInstance(project).getPreviewInfo(session, itemData, isAllTab, project)
  }

  override suspend fun isPreviewEnabled(
    projectId: ProjectId,
    session: SeSession,
    dataContextId: DataContextId,
    providerIds: List<SeProviderId>,
    isAllTab: Boolean,
  ): Boolean {
    val project = projectId.findProjectOrNull() ?: return false
    return SeBackendService.getInstance(project).isPreviewEnabled(session, dataContextId, providerIds, isAllTab)
  }

  override suspend fun isExtendedInfoEnabled(projectId: ProjectId, session: SeSession, dataContextId: DataContextId, providerIds: List<SeProviderId>, isAllTab: Boolean): Boolean {
    val project = projectId.findProjectOrNull() ?: return false
    return SeBackendService.getInstance(project).isExtendedInfoEnabled(session, dataContextId, providerIds, isAllTab)
  }

  override suspend fun isCommandsSupported(projectId: ProjectId, session: SeSession, dataContextId: DataContextId, providerIds: List<SeProviderId>, isAllTab: Boolean): Boolean {
    val project = projectId.findProjectOrNull() ?: return false
    return SeBackendService.getInstance(project).isCommandsSupported(session, dataContextId, providerIds, isAllTab)
  }
}
