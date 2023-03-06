// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.popup.JBPopup
import java.awt.Component

interface PopupComponentFactory {
  companion object {
    @JvmStatic
    fun getCurrentInstance(): PopupComponentFactory = ApplicationManager.getApplication().getService(PopupComponentFactory::class.java)
  }
  enum class PopupType {
    DIALOG,
    HEAVYWEIGHT,
    DEFAULT
  }
  fun getPopup(type: PopupType, owner: Component, content: Component, x: Int, y: Int, jbPopup: JBPopup): PopupComponent
}