// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SearchEverywhereItemData
import com.intellij.platform.searchEverywhere.SearchEverywhereParams
import com.intellij.platform.searchEverywhere.SearchEverywhereProviderId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class SearchEverywhereBackendService {
  fun getProviderIds(): List<SearchEverywhereProviderId> {
    return emptyList()
  }

  fun getItems(params: SearchEverywhereParams, providerId: SearchEverywhereProviderId): Flow<SearchEverywhereItemData> {
    // TODO: Implement
    return emptyFlow()
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SearchEverywhereBackendService = project.getService(SearchEverywhereBackendService::class.java)
  }
}