// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereExtendedInfoProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeAsyncContributorWrapper<I : Any>(val contributor: SearchEverywhereContributor<I>) : Disposable {
  fun fetchElements(
    pattern: String,
    progressIndicator: ProgressIndicator,
    consumer: AsyncProcessor<I>,
  ) {
    contributor.fetchElements(pattern, progressIndicator) { t ->
      runBlockingCancellable {
        SeLog.log(SeLog.ITEM_EMIT) { "Provider async wrapper of ${contributor.searchProviderId} emitting: ${t}" }
        consumer.process(t)
      }
    }
  }

  override fun dispose() {
    Disposer.dispose(contributor)
  }
}

@ApiStatus.Internal
fun SearchEverywhereContributor<*>.getExtendedDescription(item: Any): String? {
  return (this as? SearchEverywhereExtendedInfoProvider)?.createExtendedInfo()?.leftText?.invoke(item)
}