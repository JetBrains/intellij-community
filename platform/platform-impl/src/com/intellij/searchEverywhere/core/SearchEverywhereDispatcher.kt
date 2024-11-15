// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere.core

import com.intellij.searchEverywhere.SearchEverywhereViewItem
import com.intellij.searchEverywhere.SearchEverywhereViewItemsProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

@ApiStatus.Internal
interface SearchEverywhereDispatcher {
  suspend fun <Param> search(
    providers: Collection<SearchEverywhereViewItemsProvider<*, *, Param>>,
    providersAndLimits: Map<SearchEverywhereViewItemsProvider<*, *, Param>, Int>,
    pattern: Param,
    alreadyFoundResults: List<SearchEverywhereViewItem<*, *>>,
  ): Flow<SearchEverywhereViewItem<*, *>>
}

@OptIn(ExperimentalCoroutinesApi::class)
@ApiStatus.Internal
class DefaultSearchEverywhereDispatcher : SearchEverywhereDispatcher {
  private data class ItemWithProvider<Param>(val provider: SearchEverywhereViewItemsProvider<*, *, Param>,
                                             val viewItem: SearchEverywhereViewItem<*, *>)

  override suspend fun <Param> search(
    providers: Collection<SearchEverywhereViewItemsProvider<*, *, Param>>,
    providersAndLimits: Map<SearchEverywhereViewItemsProvider<*, *, Param>, Int>,
    pattern: Param,
    alreadyFoundResults: List<SearchEverywhereViewItem<*, *>>,
  ): Flow<SearchEverywhereViewItem<*, *>> {
    val newResultsCount = ConcurrentHashMap<SearchEverywhereViewItemsProvider<*, *, Param>, Int>()

    // This collection should be a HashSet, but SearchEverywhereViewItem is not comparable.
    // With current approach results filtering is O(n^2)
    // TODO: Use a proper data structure
    val results = ConcurrentLinkedQueue<SearchEverywhereViewItem<*, *>>()
    results.addAll(alreadyFoundResults)

    fun isLimitReached(provider: SearchEverywhereViewItemsProvider<*, *, Param>): Boolean {
      val limit = providersAndLimits[provider] ?: return false
      val count = newResultsCount[provider] ?: return false
      return count >= limit
    }

    return providers.asFlow().flatMapMerge { provider ->
      provider.processViewItems(pattern).takeWhile {
        !isLimitReached(provider)
      }.map {
        ItemWithProvider(provider, it)
      }
    }.filter { itemWithProvider ->
      val isNew = results.indexOfFirst { o ->
        // Comparator
        itemWithProvider.viewItem.item == o.item
      } < 0

      if (!isNew) return@filter false

      if (isLimitReached(itemWithProvider.provider)) return@filter false
      else {
        results.add(itemWithProvider.viewItem)

        if (providersAndLimits[itemWithProvider.provider] == null) true
        else {
          val count = newResultsCount.getOrDefault(itemWithProvider.provider, 0)
          newResultsCount[itemWithProvider.provider] = count + 1
          true
        }
      }
    }.map {
      it.viewItem
    }
  }
}