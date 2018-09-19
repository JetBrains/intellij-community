// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ui.components.JBLayeredPane
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JScrollPane

private class ScrollPaneOverlayPanel(val scrollPane: JScrollPane) : JBLayeredPane() {
  var topRightOffset = Point()
  var topRightComponent: JComponent? = null
    set(value) {
      if (field != null) remove(field)
      field = value
      add(field, JLayeredPane.POPUP_LAYER as Any)
    }

  init {
    add(scrollPane, JLayeredPane.DEFAULT_LAYER as Any)
  }

  override fun doLayout() {
    val scrollPaneBounds = Rectangle(Point(), size)
    scrollPane.bounds = scrollPaneBounds

    topRightComponent?.let { layoutTopRight(it, scrollPaneBounds, topRightOffset) }
  }

  private fun layoutTopRight(component: JComponent, outerBounds: Rectangle, offset: Point) {
    val componentSize = component.preferredSize
    val point = Point(outerBounds.x + outerBounds.width, outerBounds.y)
    point.translate(-offset.x - componentSize.width, offset.y)
    component.bounds = Rectangle(point, componentSize)
  }
}