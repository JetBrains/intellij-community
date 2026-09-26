// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.util.ui.JBUI
import java.awt.Point
import java.awt.Rectangle
import kotlin.math.max

internal class ToolWindowDropArea(val bounds: Rectangle, val isVertical: Boolean, splitter: Splitter?) {
  val hasBothComponents: Boolean
  private val firstBounds: Rectangle
  private val secondBounds: Rectangle

  init {
    val first = splitter?.firstComponent
    val second = splitter?.secondComponent
    if (first != null && second != null) {
      hasBothComponents = true
      firstBounds = first.bounds.also { it.translate(bounds.x, bounds.y) }
      secondBounds = second.bounds.also { it.translate(bounds.x, bounds.y) }
    }
    else {
      hasBothComponents = false
      firstBounds = Rectangle(bounds).also { splitDropTargetBounds(it, isVertical, isSplit = false) }
      secondBounds = Rectangle(bounds).also { splitDropTargetBounds(it, isVertical, isSplit = true) }
    }
  }

  fun isSplit(screenPoint: Point): Boolean {
    return if (isVertical) screenPoint.y >= secondBounds.y else screenPoint.x >= secondBounds.x
  }

  fun containsPoint(screenPoint: Point, split: Boolean? = null): Boolean {
    return bounds.contains(screenPoint) && (split == null || isSplit(screenPoint) == split)
  }

  fun getTargetBounds(isSplit: Boolean): Rectangle = Rectangle(if (isSplit) secondBounds else firstBounds)
}

internal fun splitDropTargetBounds(bounds: Rectangle, isVertical: Boolean, isSplit: Boolean) {
  if (isVertical) {
    val half = bounds.height / 2
    bounds.height = if (isSplit) bounds.height - half else half
    if (isSplit) {
      bounds.y += half
    }
  }
  else {
    val half = bounds.width / 2
    bounds.width = if (isSplit) bounds.width - half else half
    if (isSplit) {
      bounds.x += half
    }
  }
}

internal fun getToolWindowDropAreaBounds(
  anchor: ToolWindowAnchor,
  paneBounds: Rectangle,
  documentBounds: Rectangle?,
  componentBounds: Rectangle?,
  sideWidth: Int,
): Rectangle {
  if (componentBounds != null) {
    return Rectangle(componentBounds)
  }

  val bounds = Rectangle(paneBounds)
  if (anchor.isHorizontal) {
    bounds.height = max(JBUI.scale(200), paneBounds.height / 4)
    if (anchor == ToolWindowAnchor.BOTTOM) {
      bounds.y += paneBounds.height - bounds.height
    }
  }
  else {
    bounds.width = sideWidth
    if (anchor == ToolWindowAnchor.RIGHT) {
      bounds.x += paneBounds.width - bounds.width
    }
    if (documentBounds != null) {
      bounds.y = documentBounds.y
      bounds.height = documentBounds.height
    }
  }
  return bounds
}
