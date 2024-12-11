// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.platform.project.ProjectId
import com.intellij.platform.searchEverywhere.SearchEverywhereItemData
import com.intellij.platform.searchEverywhere.SearchEverywhereItemDataProvider
import com.intellij.platform.searchEverywhere.SearchEverywhereParams
import com.intellij.platform.searchEverywhere.SearchEverywhereProviderId
import com.intellij.platform.searchEverywhere.impl.SearchEverywhereRemoteApi
import com.jetbrains.rhizomedb.EID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SearchEverywhereItemDataFrontendProvider(private val projectId: ProjectId,
                                               override val id: SearchEverywhereProviderId): SearchEverywhereItemDataProvider {
  override fun getItems(sessionId: EID, params: SearchEverywhereParams): Flow<SearchEverywhereItemData> {
    return flow {
      SearchEverywhereRemoteApi.getInstance().getItems(projectId, sessionId, id, params)
    }
  }
}