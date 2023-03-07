// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup

import com.intellij.openapi.components.service
import com.intellij.openapi.ui.popup.JBPopup
import org.jetbrains.annotations.ApiStatus
import java.awt.Component

@ApiStatus.Internal
@ApiStatus.Experimental
interface PopupComponentFactory {
  companion object {
    @JvmStatic
    fun getCurrentInstance(): PopupComponentFactory = service()
  }
  enum class PopupType {
    DIALOG,
    HEAVYWEIGHT,
    DEFAULT
  }
  fun createPopupComponent(type: PopupType, owner: Component, content: Component, x: Int, y: Int, jbPopup: JBPopup): PopupComponent
}