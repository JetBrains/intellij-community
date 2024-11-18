// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.searchEverywhere.mocks

import com.intellij.searchEverywhere.SearchEverywhereItemsProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchEverywhereItemsProviderMock(
  private val resultPrefix: String = "item",
  private val size: Int = 100,
  private val delayMillis: Long = 0,
  private val delayStep: Int = 0,
) : SearchEverywhereItemsProvider<String, String> {
  override fun processItems(searchParams: String): Flow<SearchEverywhereItemsProvider.WeightedItem<String>> {
    return flow {
      if (delayStep <= 0) delay(delayMillis)

      repeat(size) { index ->
        val item = "$resultPrefix $index"
        emit(SearchEverywhereItemsProvider.WeightedItem(item, index))
        if (delayStep > 0 && delayMillis > 0 && (index + 1) % delayStep == 0) {
          delay(delayMillis)
        }
      }
    }
  }
}