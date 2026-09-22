// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import java.awt.Rectangle
import java.awt.geom.Rectangle2D

internal data class CacheRequest(
  val key: EditorAnimationCacheKey,
  private val rectangles: List<Rectangle2D>,
) {
  fun isDuplicate(previousKey: EditorAnimationCacheKey?): Boolean {
    return key == previousKey
  }

  /**
   * The single zone this request needs cached, or `null` when nothing of it is on screen.
   *
   * The union is taken before clipping to [visibleArea], because that is the order Swing paints in: it coalesces the
   * repaint requests into their bounding box first, and only the resulting clip is visible-bound.
   */
  fun repaintedArea(visibleArea: Rectangle, pixelGrid: EditorPixelGrid): Rectangle2D? {
    val requested = rectangles
    val boundingBox = requested.boundingBox() ?: return null
    val visiblePart = boundingBox.intersectWithVisibleArea(visibleArea) ?: return null
    return pixelGrid.align(visiblePart)
  }

  private fun List<Rectangle2D>.boundingBox(): Rectangle2D? {
    return reduceOrNull { union, rectangle -> union.createUnion(rectangle) }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as CacheRequest
    if (key != other.key) return false
    return true
  }

  override fun hashCode(): Int {
    return key.hashCode()
  }
}
