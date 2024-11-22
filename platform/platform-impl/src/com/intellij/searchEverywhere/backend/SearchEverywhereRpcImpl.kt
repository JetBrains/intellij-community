// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere.backend

import com.intellij.searchEverywhere.shared.SearchEverywhereItemData
import com.intellij.searchEverywhere.shared.SearchEverywhereParams
import com.intellij.searchEverywhere.shared.SearchEverywhereRpc
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchEverywhereRpcImpl: SearchEverywhereRpc {
  override suspend fun itemSelected(itemDate: SearchEverywhereItemData) {
    TODO("Not yet implemented")
  }

  override fun getItems(params: SearchEverywhereParams): Flow<SearchEverywhereItemData> {
    TODO("Not yet implemented")
  }
}
