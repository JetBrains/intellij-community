// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.mocks

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.searchEverywhere.*
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.SeLog.*
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
  override val displayName: String,
  private val size: Int = 100,
  private val delayMillis: Long = 0,
  private val delayStep: Int = 0,
) : SeItemsProvider {

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    coroutineScope {
      val flow: Flow<SeItemMock> = flow {
        delay(delayMillis)

        repeat(size) { index ->
          val item = SeItemMock("$resultPrefix $index - ${params.inputQuery}")

          if (params.inputQuery.isEmpty() || item.text.contains(params.inputQuery, ignoreCase = true)) {
            SeLog.log(ITEM_EMIT) { "Provider ${id} emitting: ${item.text}" }
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
    SeLog.logSuspendable(USER_ACTION) { "Provider ${id} item selected: ${item.presentation().text}" }
    return true
  }

  override fun dispose() {
    SeLog.log(LIFE_CYCLE, "Provider mock ${id} disposed")
  }
}

@ApiStatus.Internal
class SeItemMock(val text: @NlsSafe String) : SeItem {
  override fun weight(): Int = 0
  override suspend fun presentation(): SeItemPresentation = SeSimpleItemPresentation(text = text)
}
