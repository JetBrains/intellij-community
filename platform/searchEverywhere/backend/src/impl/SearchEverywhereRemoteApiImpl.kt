// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.searchEverywhere.SearchEverywhereItemData
import com.intellij.platform.searchEverywhere.SearchEverywhereParams
import com.intellij.platform.searchEverywhere.SearchEverywhereProviderId
import com.intellij.platform.searchEverywhere.impl.SearchEverywhereRemoteApi
import com.jetbrains.rhizomedb.EID
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchEverywhereRemoteApiImpl: SearchEverywhereRemoteApi {
  override suspend fun startSession(projectId: ProjectId): EID {
    return SearchEverywhereBackendService.getInstance(projectId.findProject()).startSession()
  }

  override suspend fun closeSession(projectId: ProjectId, sessionId: EID) {
    return SearchEverywhereBackendService.getInstance(projectId.findProject()).closeSession(sessionId)
  }

  override suspend fun getProviderIds(projectId: ProjectId, sessionId: EID): List<SearchEverywhereProviderId> {
    return SearchEverywhereBackendService.getInstance(projectId.findProject()).getProviderIds(sessionId)
  }

  override suspend fun itemSelected(projectId: ProjectId, itemId: EID) {
    SearchEverywhereBackendService.getInstance(projectId.findProject()).itemSelected(itemId)
  }

  override suspend fun getItems(projectId: ProjectId,
                                sessionId: EID,
                                providerId: SearchEverywhereProviderId,
                                params: SearchEverywhereParams): Flow<SearchEverywhereItemData> {
    return SearchEverywhereBackendService.getInstance(projectId.findProject()).getItems(sessionId, providerId, params)
  }
}
