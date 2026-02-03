// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup.list

import com.intellij.openapi.ui.popup.ListPopup

interface ListPopupWrapper : ListPopup {
  val basePopup: ListPopup

  companion object {
    @JvmStatic
    fun getRootPopup(popup: ListPopup): ListPopup {
      var currentPopup = popup
      while (currentPopup is ListPopupWrapper) {
        currentPopup = currentPopup.basePopup
      }
      return currentPopup
    }
  }
}