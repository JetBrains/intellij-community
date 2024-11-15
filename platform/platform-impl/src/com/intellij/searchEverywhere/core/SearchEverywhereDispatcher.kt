// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere.core

import com.intellij.searchEverywhere.SearchEverywhereViewItem
import com.intellij.searchEverywhere.SearchEverywhereViewItemsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface SearchEverywhereDispatcher {
  suspend fun <Param> search(
    scope: CoroutineScope,
    providers: Collection<SearchEverywhereViewItemsProvider<*, *, Param>>,
    providersAndLimits: Map<SearchEverywhereViewItemsProvider<*, *, Param>, Int?>,
    pattern: Param,
    alreadyFoundResults: List<SearchEverywhereViewItem<*, *>>,
    processor: (SearchEverywhereViewItem<*, *>) -> Boolean,
  )
}

@ApiStatus.Internal
class DefaultSearchEverywhereDispatcher: SearchEverywhereDispatcher {


  override suspend fun <Param> search(
    scope: CoroutineScope,
    providers: Collection<SearchEverywhereViewItemsProvider<*, *, Param>>,
    providersAndLimits: Map<SearchEverywhereViewItemsProvider<*, *, Param>, Int?>,
    pattern: Param,
    alreadyFoundResults: List<SearchEverywhereViewItem<*, *>>,
    processor: (SearchEverywhereViewItem<*, *>) -> Boolean,
  ) {
    val overallLimit = providersAndLimits.values.filterNotNull().sum()
    val resultsSharedFlow = MutableSharedFlow<SearchEverywhereViewItem<*, *>>(replay = overallLimit)

    coroutineScope {
      providers.map { provider ->
        provider.processViewItems(pattern)
      }.forEach { providerResultsFlow ->

      }
    }
  }
}