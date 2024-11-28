// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcsUtil

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.ui.popup.PopupShowOptions
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Point

fun JBPopup.showAbove(component: Component): Unit = VcsUIUtil.showPopupAbove(this, component, null)

object VcsUIUtil {
  fun showPopupAbove(popup: JBPopup, component: Component) {
    showPopupAbove(popup, component, null)
  }

  fun showPopupAbove(popup: JBPopup, component: Component, minHeight: Int?) {
    popup.showAboveOf(
      component,
      PopupShowOptions()
        .withPopupComponentUnscaledGap(4)
        .withMinimumHeight(minHeight)
    )
  }
}