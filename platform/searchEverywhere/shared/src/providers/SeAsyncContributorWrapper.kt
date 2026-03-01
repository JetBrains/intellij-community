// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereExtendedInfoProvider
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.ide.util.DelegatingProgressIndicator
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import com.intellij.platform.searchEverywhere.SeExtendedInfoBuilder
import com.intellij.platform.searchEverywhere.SeItemsProviderWithPossibleOperationDisposable
import com.intellij.platform.searchEverywhere.providers.SeLog.ITEM_EMIT
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeAsyncContributorWrapper<I : Any>(val contributor: SearchEverywhereContributor<I>) : Disposable {
  suspend fun fetchElements(pattern: String, consumer: AsyncProcessor<I>, operationDisposable: Disposable? = null) {
    coroutineToIndicator {
      val indicator = DelegatingProgressIndicator(ProgressManager.getGlobalProgressIndicator())
      if (pattern.isEmpty() && !contributor.isEmptyPatternSupported) return@coroutineToIndicator

      if (contributor is WeightedSearchEverywhereContributor) {
        contributor.fetchWeightedElements(pattern, indicator) { t ->
          runBlockingCancellable {
            SeLog.log(ITEM_EMIT) {
              "Provider async wrapper of ${contributor.searchProviderId} emitting: ${t.item.toString().split('\n').firstOrNull()}"
            }
            consumer.process(t.item, t.weight)
          }
        }
      }
      else {
        contributor.fetchElements(pattern, indicator) { t ->
          runBlockingCancellable {
            SeLog.log(SeLog.ITEM_EMIT) {
              "Provider async wrapper of ${contributor.searchProviderId} emitting: ${t.toString().split('\n').firstOrNull()}"
            }
            val weight = contributor.getElementPriority(t, pattern)
            consumer.process(t, weight)
          }
        }
      }
    }
  }

  override fun dispose() {
    Disposer.dispose(contributor)
  }
}

@Internal
interface AsyncProcessor<T> {
  suspend fun process(item: T, weight: Int): Boolean
}

@Internal
fun SearchEverywhereContributor<*>.getExtendedInfo(item: Any): SeExtendedInfo {
  val extendedInfo = (this as? SearchEverywhereExtendedInfoProvider)?.createExtendedInfo()
  return SeExtendedInfoBuilder().withExtendedInfo(extendedInfo, item).build()
}