// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import com.intellij.openapi.editor.impl.caret.model.CaretRectangle
import java.awt.Rectangle
import java.awt.geom.Rectangle2D
import java.util.concurrent.atomic.AtomicReference

/**
 * Identifies the content a cache request covers, so a request for content already held can be dropped.
 */
internal data class EditorAnimationCacheKey(private val contentHash: Int) {
  companion object {
    @JvmStatic
    fun of(locations: List<CaretRectangle>): EditorAnimationCacheKey {
      var hash = locations.size
      for (location in locations) {
        hash = hash * CACHE_KEY_HASH_FACTOR + location.contentHash
      }
      return EditorAnimationCacheKey(hash)
    }

    private const val CACHE_KEY_HASH_FACTOR = 31
  }
}

internal data class CacheRequest(
  private val key: EditorAnimationCacheKey,
  private val rectangles: () -> List<Rectangle2D>,
) {
  /**
   * The single zone this request needs cached, or `null` when nothing of it is on screen.
   *
   * The union is taken before clipping to [visibleArea], because that is the order Swing paints in: it coalesces the
   * repaint requests into their bounding box first, and only the resulting clip is visible-bound.
   */
  fun repaintedArea(visibleArea: Rectangle, pixelGrid: EditorPixelGrid): Rectangle2D? {
    val requested = rectangles()
    val boundingBox = requested.boundingBox() ?: return null
    val visiblePart = boundingBox.intersectWithVisibleArea(visibleArea) ?: return null
    return pixelGrid.align(visiblePart)
  }

  fun isDuplicate(previousKey: EditorAnimationCacheKey?): Boolean = key == previousKey

  /**
   * Records this request's key as the content the cache now holds.
   */
  fun storeKeyIn(atomicKey: AtomicReference<EditorAnimationCacheKey?>) {
    atomicKey.set(key)
  }
}

private fun List<Rectangle2D>.boundingBox(): Rectangle2D? {
  return reduceOrNull { union, rectangle -> union.createUnion(rectangle) }
}
