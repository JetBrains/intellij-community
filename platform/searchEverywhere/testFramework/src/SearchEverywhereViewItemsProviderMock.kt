// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.testFramework

import com.intellij.platform.searchEverywhere.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchEverywhereItemsProviderMock(
  val resultPrefix: String = "item",
  private val size: Int = 100,
  private val delayMillis: Long = 0,
  private val delayStep: Int = 0,
) : SearchEverywhereItemsProvider {
  override val id = "SearchEverywhereItemsProviderMock_$resultPrefix"

  override fun getItems(params: SearchEverywhereParams): Flow<SearchEverywhereItem> {
    return flow {
      if (delayStep <= 0) delay(delayMillis)

      repeat(size) { index ->
        val item = SearchEverywhereItemMock("$resultPrefix $index")
        emit(item)
        if (delayStep > 0 && delayMillis > 0 && (index + 1) % delayStep == 0) {
          delay(delayMillis)
        }
      }
    }
  }
}

@ApiStatus.Internal
class SearchEverywhereItemMock(val text: String) : SearchEverywhereItem {
  override fun weight(): Int = 0
  override fun presentation(): SearchEverywhereItemPresentation = ActionItemPresentation(text = text)
}
