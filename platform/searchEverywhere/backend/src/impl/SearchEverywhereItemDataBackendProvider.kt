// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.platform.searchEverywhere.*
import com.jetbrains.rhizomedb.EID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

class SearchEverywhereItemDataBackendProvider(override val id: SearchEverywhereProviderId,
                                              private val provider: SearchEverywhereItemsProvider): SearchEverywhereItemDataProvider {
  override fun getItems(sessionId: EID, params: SearchEverywhereParams): Flow<SearchEverywhereItemData> {
    return provider.getItems(params).mapNotNull { item ->
      SearchEverywhereItemData.createItemData(sessionId, item, id, item.weight(), item.presentation())
    }
  }
}