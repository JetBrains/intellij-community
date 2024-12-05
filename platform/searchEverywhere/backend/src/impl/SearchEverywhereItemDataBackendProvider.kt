// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.platform.searchEverywhere.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SearchEverywhereItemDataBackendProvider(override val id: SearchEverywhereProviderId,
                                              private val provider: SearchEverywhereItemsProvider): SearchEverywhereItemDataProvider {
  override fun getItems(params: SearchEverywhereParams, session: SearchEverywhereSession): Flow<SearchEverywhereItemData> {
    return provider.getItems(params).map {
      val itemId = session.saveItem(it)
      SearchEverywhereItemData(itemId, SearchEverywhereProviderId(provider.id), it.weight(), it.presentation())
    }
  }
}