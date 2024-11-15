// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere.core

import com.intellij.searchEverywhere.SearchEverywhereViewItem
import com.intellij.searchEverywhere.SearchEverywhereViewItemsProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SearchEverywhereDispatcher {
  suspend fun <Param> search(
    providers: Collection<SearchEverywhereViewItemsProvider<*, *, Param>>,
    providersAndLimits: Map<SearchEverywhereViewItemsProvider<*, *, Param>, Int?>,
    pattern: Param,
    alreadyFoundResults: List<SearchEverywhereViewItem<*, *>>,
  ): Flow<SearchEverywhereViewItem<*, *>>
}

@OptIn(ExperimentalCoroutinesApi::class)
@ApiStatus.Internal
class DefaultSearchEverywhereDispatcher : SearchEverywhereDispatcher {

  override suspend fun <Param> search(
    providers: Collection<SearchEverywhereViewItemsProvider<*, *, Param>>,
    providersAndLimits: Map<SearchEverywhereViewItemsProvider<*, *, Param>, Int?>,
    pattern: Param,
    alreadyFoundResults: List<SearchEverywhereViewItem<*, *>>,
  ): Flow<SearchEverywhereViewItem<*, *>> {
    return providers.asFlow().flatMapMerge { provider ->
      provider.processViewItems(pattern)
    }
  }
}