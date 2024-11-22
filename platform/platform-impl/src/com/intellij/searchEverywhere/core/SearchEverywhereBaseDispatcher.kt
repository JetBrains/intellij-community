// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere.core

import com.intellij.searchEverywhere.shared.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@OptIn(ExperimentalCoroutinesApi::class)
open class SearchEverywhereBaseDispatcher(): SearchEverywhereDispatcher {

  override fun getItems(params: SearchEverywhereParams,
                        providers: Collection<SearchEverywhereItemsProvider>,
                        providersAndLimits: Map<SearchEverywhereProviderId, Int>,
                        alreadyFoundResults: List<SearchEverywhereItemData>): Flow<SearchEverywhereItemData> {

    val accumulator = SearchEverywhereResultsAccumulator(providersAndLimits, alreadyFoundResults)

    return providers.asFlow().flatMapMerge { provider ->
      provider.getItems(params).mapNotNull {
        when (accumulator.add(it)) {
          is SearchEverywhereResultsAccumulator.Added, is SearchEverywhereResultsAccumulator.Replaced -> it
          else -> null
        }
      }
    }
  }
}
