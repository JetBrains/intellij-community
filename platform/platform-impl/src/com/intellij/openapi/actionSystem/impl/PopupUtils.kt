// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.AnchoredPoint
import com.intellij.ui.popup.util.PopupImplUtil
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Dimension
import java.awt.Point

object PopupUtils {

  /**
   * Attaches the given JBPopup to the root component (e.g. parent popup),
   * ensuring that it is displayed within the visible bounds of the screen.
   *
   * @param offset The offset from the component's location to position the popup
   */
  @JvmStatic
  fun attachToWindowComponent(popup: JBPopup, component: Component?, offset: Point) {
    popup.addListener(object : JBPopupListener {
      override fun beforeShown(event: LightweightWindowEvent) {
        val rootPane = UIUtil.getRootPane(component) ?: return
        popup.setMinimumSize(Dimension(rootPane.width, 0))
        val verticalGap = 2
        val point = AnchoredPoint(AnchoredPoint.Anchor.BOTTOM_LEFT, rootPane, Point(0, verticalGap)).screenPoint
        val screenRectangle = ScreenUtil.getScreenRectangle(point)
        val popupSize = PopupImplUtil.getPopupSize(popup)
        if (point.x + popupSize.width > screenRectangle.x + screenRectangle.width) { //horizontal overflow
          point.x += rootPane.size.width - popupSize.width
        }
        if (point.y + popupSize.height > screenRectangle.y + screenRectangle.height) { //vertical overflow
          val pointTopLeft = AnchoredPoint(AnchoredPoint.Anchor.TOP_LEFT, rootPane, Point(0, -verticalGap)).screenPoint
          point.y = pointTopLeft.y - popupSize.height
        }
        point.translate(offset.x, offset.y)
        popup.setLocation(point)
      }
    })
  }


}