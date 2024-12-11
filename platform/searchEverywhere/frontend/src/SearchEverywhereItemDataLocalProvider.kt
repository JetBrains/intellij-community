// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.platform.searchEverywhere.*
import com.jetbrains.rhizomedb.EID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SearchEverywhereItemDataLocalProvider(private val itemsProvider: SearchEverywhereItemsProvider): SearchEverywhereItemDataProvider {
  override val id: SearchEverywhereProviderId
    get() = SearchEverywhereProviderId(itemsProvider.id)

  override fun getItems(sessionId: EID, params: SearchEverywhereParams): Flow<SearchEverywhereItemData> {
    return itemsProvider.getItems(params).mapNotNull {
      SearchEverywhereItemData.createItemData(sessionId, it, id, it.weight(), it.presentation())
    }
  }
}