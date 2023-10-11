// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcsUtil

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.awt.RelativePoint
import java.awt.Component
import java.awt.Point

fun JBPopup.showAbove(component: Component): Unit = VcsUIUtil.showPopupAbove(this, component)

object VcsUIUtil {
  fun showPopupAbove(popup: JBPopup, component: Component) {
    val northWest = RelativePoint(component, Point())

    popup.addListener(object : JBPopupListener {
      override fun beforeShown(event: LightweightWindowEvent) {
        val location = Point(popup.locationOnScreen).apply { y = northWest.screenPoint.y - popup.size.height }

        popup.setLocation(location)
        popup.removeListener(this)
      }
    })
    popup.show(northWest)
  }
}