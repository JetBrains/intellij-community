// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.ide.ui.proofreading.component.list

import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep

class GrazieLanguagesPopupStep(title: String, available: List<Lang>, toDownload: List<Lang>,
                               private val download: (Lang) -> Boolean, private val onResult: (Lang) -> Unit)
  : BaseListPopupStep<Lang>(title, available + toDownload) {
  private val firstOther = toDownload.firstOrNull()

  override fun getSeparatorAbove(value: Lang) = if (value == firstOther) ListSeparator() else null

  override fun getTextFor(value: Lang) = value.nativeName

  override fun onChosen(selectedValue: Lang, finalChoice: Boolean): PopupStep<*>? {
    return doFinalStep { if (download(selectedValue)) onResult(selectedValue) }
  }
}
