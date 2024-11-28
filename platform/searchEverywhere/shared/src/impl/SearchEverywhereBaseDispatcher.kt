// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.impl

import com.intellij.platform.searchEverywhere.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.mapNotNull
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@OptIn(ExperimentalCoroutinesApi::class)
open class SearchEverywhereBaseDispatcher(
  private val providers: List<SearchEverywhereItemDataProvider>,
  private val providersAndLimits: Map<SearchEverywhereProviderId, Int>,
): SearchEverywhereDispatcher {
  private val session = SearchEverywhereSessionImpl()

  override fun getItems(params: SearchEverywhereParams,
                        alreadyFoundResults: List<SearchEverywhereItemData>): Flow<SearchEverywhereItemData> {

    val accumulator = SearchEverywhereResultsAccumulator(providersAndLimits, alreadyFoundResults)

    return providers.asFlow().flatMapMerge { provider ->
      provider.getItems(params, session).mapNotNull {
        val event = accumulator.add(it)
        when {
          event.isAdded || event.isReplaced -> it
          else -> null
        }
      }
    }
  }
}