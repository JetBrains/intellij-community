// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.awt

import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JComponent

class AnchoredPoint @JvmOverloads constructor(val anchor: Anchor,
                                              component: Component,
                                              val offset: Point = Point(0, 0))
  : RelativePoint(component, anchor.getPointOnComponent(component, offset)) {

  enum class Anchor {
    CENTER {
      override fun Rectangle.getPointOnRectangle(): Point =
        Point(x + width / 2, y + height / 2)
    },
    LEFT {
      override fun Rectangle.getPointOnRectangle(): Point =
        Point(x, y + height / 2)
    },
    RIGHT {
      override fun Rectangle.getPointOnRectangle(): Point =
        Point(x + width, y + height / 2)
    },
    TOP {
      override fun Rectangle.getPointOnRectangle(): Point =
        Point(x + width / 2, y)
    },
    BOTTOM {
      override fun Rectangle.getPointOnRectangle(): Point =
        Point(x + width / 2, y + height)
    },
    TOP_LEFT {
      override fun Rectangle.getPointOnRectangle(): Point =
        Point(x, y)
    },
    BOTTOM_LEFT {
      override fun Rectangle.getPointOnRectangle(): Point =
        Point(x, y + height)
    },
    TOP_RIGHT {
      override fun Rectangle.getPointOnRectangle(): Point =
        Point(x + width, y)
    },
    BOTTOM_RIGHT {
      override fun Rectangle.getPointOnRectangle(): Point =
        Point(x + width, y + height)
    };

    protected abstract fun Rectangle.getPointOnRectangle(): Point

    internal fun getPointOnComponent(component: Component, offset: Point = Point(0, 0)): Point {
      val bounds = if (component is JComponent && !component.visibleRect.isEmpty) {
        component.visibleRect
      }
      else {
        Rectangle(component.x, component.y, component.width, component.height)
      }
      return bounds.getPointOnRectangle().apply {
        x += offset.x
        y += offset.y
      }
    }
  }
}