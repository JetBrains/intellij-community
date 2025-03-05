// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcsUtil

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.PopupShowOptions
import java.awt.Component

fun JBPopup.showAbove(component: Component): Unit = VcsUIUtil.showPopupAbove(this, component, null)

object VcsUIUtil {
  fun showPopupAbove(popup: JBPopup, component: Component) {
    showPopupAbove(popup, component, null)
  }

  fun showPopupAbove(popup: JBPopup, component: Component, minHeight: Int?) {
    popup.show(PopupShowOptions.aboveComponent(component)
                 .withPopupComponentUnscaledGap(4)
                 .withMinimumHeight(minHeight))
  }
}