// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.platform.searchEverywhere.SeItemPresentation
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeTextItemPresentation
import com.intellij.platform.searchEverywhere.SeTextSearchParams
import com.intellij.platform.searchEverywhere.api.SeItem
import com.intellij.platform.searchEverywhere.api.SeItemsProvider
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeItemsProviderMock(
  val resultPrefix: String = "item",
  override val id: String = "SearchEverywhereItemsProviderMock_$resultPrefix",
  private val size: Int = 100,
  private val delayMillis: Long = 0,
  private val delayStep: Int = 0,
) : SeItemsProvider {

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val searchText = (params as? SeTextSearchParams)?.text ?: return

    coroutineScope {
      val flow: Flow<SeItemMock> = flow {
        delay(delayMillis)

        repeat(size) { index ->
          val item = SeItemMock("$resultPrefix $index")

          if (searchText.isEmpty() || item.text.contains(searchText, ignoreCase = true)) {
            emit(item)
          }

          if (delayStep > 0 && delayMillis > 0 && (index + 1) % delayStep == 0) {
            delay(delayMillis)
          }
        }
      }

      flow.collect {
        val shouldContinue = collector.put(it)
        if (!shouldContinue) cancel("Canceled by collector")
      }
    }
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    println("item selected: ${item.presentation().text} - ${item}")
    return true
  }
}

@ApiStatus.Internal
class SeItemMock(val text: String) : SeItem {
  override fun weight(): Int = 0
  override fun presentation(): SeItemPresentation = SeTextItemPresentation(text = text)
}
