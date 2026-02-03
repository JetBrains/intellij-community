// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content

import com.intellij.openapi.ui.popup.ActiveIcon
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.impl.content.tabActions.ContentTabAction
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle

/**
 * @author graann on 08/02/2018
 */
@ApiStatus.Internal
abstract class AdditionalIcon(val action: ContentTabAction) {
  val icon: ActiveIcon get() = action.icon
  val afterText: Boolean get() = action.afterText

  val available: Boolean get() = action.available
  fun runAction(): Unit = action.runAction()

  @get:NlsContexts.Tooltip
  val tooltip: String?
    get() = action.tooltip


  abstract val active: Boolean
  abstract val height: Int
  var x: Int = 0


  val centerPoint: Point
    get() = Point(x + (getIconWidth() / 2), getIconY())

  fun paintIcon(c: Component, g: Graphics) {
    icon.setActive(active)

    icon.paintIcon(c, g, x, getIconY())
  }

  fun getIconWidth(): Int {
    return icon.iconWidth
  }

  fun getIconHeight(): Int {
    return icon.iconHeight
  }

  private fun getIconY(): Int {
    return (height - getIconHeight()) / 2 + 1
  }

  fun contains(point: Point): Boolean {
    return Rectangle(x, 0, getIconWidth(), height).contains(point)
  }
}