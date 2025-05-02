// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.WeightedSearchEverywhereContributor
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.providers.SeLog.ITEM_EMIT
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeAsyncWeightedContributorWrapper<I: Any>(val contributor: WeightedSearchEverywhereContributor<I>) : Disposable {
  fun fetchWeightedElements(
    pattern: String,
    progressIndicator: ProgressIndicator,
    consumer: AsyncProcessor<FoundItemDescriptor<I>>
  ) {
    contributor.fetchWeightedElements(pattern, progressIndicator) { t ->
      runBlockingCancellable {
        SeLog.log(ITEM_EMIT) { "Provider async wrapper of ${contributor.searchProviderId} emitting: ${t.item}" }
        consumer.process(t)
      }
    }
  }

  override fun dispose() {
    Disposer.dispose(contributor)
  }
}

@Internal
interface AsyncProcessor<T> {
  suspend fun process(t: T): Boolean
}
