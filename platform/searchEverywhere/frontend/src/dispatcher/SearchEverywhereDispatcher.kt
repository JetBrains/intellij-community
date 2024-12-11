// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.dispatcher

import com.intellij.platform.searchEverywhere.SearchEverywhereItemData
import com.intellij.platform.searchEverywhere.SearchEverywhereItemDataProvider
import com.intellij.platform.searchEverywhere.SearchEverywhereParams
import com.intellij.platform.searchEverywhere.SearchEverywhereProviderId
import com.jetbrains.rhizomedb.EID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.annotations.ApiStatus

@OptIn(ExperimentalCoroutinesApi::class)
@ApiStatus.Internal
class SearchEverywhereDispatcher(
  private val providers: Collection<SearchEverywhereItemDataProvider>,
  private val providersAndLimits: Map<SearchEverywhereProviderId, Int>,
) {
  fun getItems(sessionId: EID,
               params: SearchEverywhereParams,
               alreadyFoundResults: List<SearchEverywhereItemData>): Flow<SearchEverywhereItemData> {

    val accumulator = SearchEverywhereResultsAccumulator(providersAndLimits, alreadyFoundResults)

    return providers.asFlow().flatMapMerge { provider ->
      provider.getItems(sessionId, params).mapNotNull {
        val event = accumulator.add(it)
        when {
          event.isAdded || event.isReplaced -> it
          else -> null
        }
      }
    }
  }
}