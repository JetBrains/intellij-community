// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereExtendedInfoProvider
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import com.intellij.platform.searchEverywhere.SeExtendedInfoImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeAsyncContributorWrapper<I : Any>(val contributor: SearchEverywhereContributor<I>) : Disposable {
  fun fetchElements(
    pattern: String,
    progressIndicator: ProgressIndicator,
    consumer: AsyncProcessor<I>,
  ) {
    if (pattern.isEmpty() && !contributor.isEmptyPatternSupported) return

    contributor.fetchElements(pattern, progressIndicator) { t ->
      runBlockingCancellable {
        SeLog.log(SeLog.ITEM_EMIT) {
          "Provider async wrapper of ${contributor.searchProviderId} emitting: ${t.toString().split('\n').firstOrNull()}"
        }
        consumer.process(t)
      }
    }
  }

  override fun dispose() {
    Disposer.dispose(contributor)
  }
}

@ApiStatus.Internal
fun SearchEverywhereContributor<*>.getExtendedInfo(item: Any): SeExtendedInfo {
  val extendedInfo = (this as? SearchEverywhereExtendedInfoProvider)?.createExtendedInfo()
  val leftText = extendedInfo?.leftText?.invoke(item)
  val rightAction = extendedInfo?.rightAction?.invoke(item)
  val keyStroke = rightAction?.shortcutSet?.shortcuts
    ?.filterIsInstance<KeyboardShortcut>()
    ?.firstOrNull()
    ?.firstKeyStroke

  return SeExtendedInfoImpl(leftText, rightAction?.templatePresentation?.text,
                            rightAction?.templatePresentation?.description,
                            keyStroke?.keyCode, keyStroke?.modifiers)
}