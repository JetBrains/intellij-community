// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.platform.project.ProjectId
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.impl.SearchEverywhereRemoteApi
import fleet.kernel.DurableRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest

class SearchEverywhereItemDataFrontendProvider(private val projectId: ProjectId,
                                               override val id: SearchEverywhereProviderId): SearchEverywhereItemDataProvider {
  override fun getItems(sessionRef: DurableRef<SearchEverywhereSessionEntity>, params: SearchEverywhereParams): Flow<SearchEverywhereItemData> {
    return channelFlow {
      SearchEverywhereRemoteApi.getInstance().getItems(projectId, sessionRef, id, params).collectLatest {
        send(it)
      }
    }
  }
}