// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcsUtil

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
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
    val northWest = RelativePoint(component, Point())

    popup.addListener(object : JBPopupListener {
      override fun beforeShown(event: LightweightWindowEvent) {
        val gap = JBUI.scale(4)
        val anchorY = northWest.screenPoint.y

        val popupLocation = popup.locationOnScreen
        val popupSize = popup.size
        popupLocation.y = anchorY - popup.size.height

        if (anchorY >= popup.size.height + gap) {
          popupLocation.y = anchorY - popup.size.height
          popup.setLocation(popupLocation)
        }
        else if (minHeight != null && anchorY > minHeight) {
          popupLocation.y = gap
          popupSize.height = anchorY - gap
          popup.setSize(popupLocation, popupSize)
        }
        else {
          // keep the default position
        }
        popup.removeListener(this)
      }
    })
    popup.show(northWest)
  }
}