// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.components.langlist

import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.utils.ifTrue
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep

class GrazieListPopupStep(title: String, downloadedLangs: List<Lang>, private val otherLangs: List<Lang>,
                          private val download: (Lang) -> Boolean,
                          val onResult: (Lang) -> Unit) : BaseListPopupStep<Lang>(title, downloadedLangs + otherLangs) {
  override fun getSeparatorAbove(value: Lang?) = when (value) {
    otherLangs.firstOrNull() -> ListSeparator()
    else -> null
  }

  override fun onChosen(selectedValue: Lang?, finalChoice: Boolean): PopupStep<*>? = selectedValue?.let { lang ->
    doFinalStep { download(lang).ifTrue { onResult(lang) } }
  } ?: PopupStep.FINAL_CHOICE
}
