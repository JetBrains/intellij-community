// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.content

import com.intellij.openapi.ui.popup.ActiveIcon
import java.awt.Component
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import javax.swing.Icon

/**
 * @author graann on 08/02/2018
 */

abstract class AdditionalIcon(val myIcon: ActiveIcon) {
  constructor(icon: Icon) : this(ActiveIcon(icon))

  open val tooltip: String? = null
  var x: Int = 0
  val centerPoint: Point
    get() = Point(x + (getIconWidth() / 2), getIconY())

  fun paintIcon(c: Component, g: Graphics) {
    myIcon.setActive(active)

    myIcon.paintIcon(c, g, x, getIconY())
  }

  fun getIconWidth(): Int {
    return myIcon.iconWidth
  }

  fun getIconHeight(): Int {
    return myIcon.iconHeight
  }

  abstract val rectangle: Rectangle
  abstract val active: Boolean
  abstract val available: Boolean
  abstract val action: Runnable
  open val afterText = true

  private fun getIconY(): Int {
    return rectangle.y + rectangle.height / 2 - getIconHeight() / 2 + 1
  }

  fun contains(point: Point): Boolean {
    return rectangle.contains(point)
  }
}