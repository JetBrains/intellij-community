// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.proofreading.component.list

import com.intellij.grazie.GrazieScope
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val logger = logger<GrazieLanguagesPopupStep>()

class GrazieLanguagesPopupStep(
  @NlsContexts.PopupTitle title: String, available: List<Lang>, toDownload: List<Lang>,
  private val download: suspend (Collection<Lang>) -> Unit, private val onResult: (Lang) -> Unit,
) : BaseListPopupStep<Lang>(title, available + toDownload) {
  private val firstOther = toDownload.firstOrNull()

  override fun getSeparatorAbove(value: Lang) = if (value == firstOther) ListSeparator() else null

  @NlsSafe
  override fun getTextFor(value: Lang) = value.nativeName

  override fun onChosen(selectedValue: Lang, finalChoice: Boolean): PopupStep<*>? {
    return doFinalStep {
      GrazieScope.coroutineScope().launch {
        try {
          download(listOf(selectedValue))
          withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            onResult(selectedValue)
          }
        }
        catch (e: Exception) {
          logger.warn("Failed to download language '$selectedValue'", e)
        }
      }
    }
  }
}
