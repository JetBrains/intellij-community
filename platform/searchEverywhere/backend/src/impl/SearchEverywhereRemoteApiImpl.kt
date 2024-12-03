// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.searchEverywhere.SearchEverywhereItemData
import com.intellij.platform.searchEverywhere.SearchEverywhereParams
import com.intellij.platform.searchEverywhere.SearchEverywhereProviderId
import com.intellij.platform.searchEverywhere.SearchEverywhereRemoteApi
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchEverywhereRemoteApiImpl: SearchEverywhereRemoteApi {
  override suspend fun getProviderIds(projectId: ProjectId): List<SearchEverywhereProviderId> {
    return SearchEverywhereBackendService.getInstance(projectId.findProject()).getProviderIds()
  }

  override suspend fun itemSelected(itemDate: SearchEverywhereItemData, projectId: ProjectId) {
    TODO("Not yet implemented")
  }

  override suspend fun getItems(params: SearchEverywhereParams,
                                providerId: SearchEverywhereProviderId,
                                projectId: ProjectId): Flow<SearchEverywhereItemData> {
    return SearchEverywhereBackendService.getInstance(projectId.findProject()).getItems(params, providerId)
  }
}
