// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.platform.project.ProjectId
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.impl.SearchEverywhereRemoteApi
import com.jetbrains.rhizomedb.EID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SearchEverywhereItemDataFrontendProvider(override val id: SearchEverywhereProviderId,
                                               private val projectId: ProjectId,
                                               private val sessionId: EID): SearchEverywhereItemDataProvider {
  override fun getItems(params: SearchEverywhereParams, session: SearchEverywhereSession): Flow<SearchEverywhereItemData> {
    return flow {
      SearchEverywhereRemoteApi.getInstance().getItems(projectId, sessionId, id, params)
    }
  }
}