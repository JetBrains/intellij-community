// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.platform.searchEverywhere.SearchEverywhereItemData
import com.intellij.platform.searchEverywhere.SearchEverywhereItemDataProvider
import com.intellij.platform.searchEverywhere.SearchEverywhereParams
import com.intellij.platform.searchEverywhere.SearchEverywhereProviderId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@OptIn(ExperimentalCoroutinesApi::class)
class SearchEverywhereDispatcher(
  private val providers: Collection<SearchEverywhereItemDataProvider>,
  private val providersAndLimits: Map<SearchEverywhereProviderId, Int>,
) {

  fun getItems(params: SearchEverywhereParams,
               alreadyFoundResults: List<SearchEverywhereItemData>): Flow<SearchEverywhereItemData> {

    return emptyFlow()
    //val session = SearchEverywhereFrontendSession()
    //val accumulator = SearchEverywhereResultsAccumulator(providersAndLimits, alreadyFoundResults)
    //
    //return providers.asFlow().flatMapMerge { provider ->
    //  provider.getItems(params, session).mapNotNull {
    //    val event = accumulator.add(it)
    //    when {
    //      event.isAdded || event.isReplaced -> it
    //      else -> null
    //    }
    //  }
    //}.onCompletion {
    //  session.dispose()
    //}
  }
}