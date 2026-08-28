// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorImageUtil.createEditorImage
import com.intellij.openapi.editor.impl.EditorImageUtil.createImageGraphics
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.animation.EditorAnimationCacheStatistics.recordHit
import com.intellij.openapi.editor.impl.view.animation.EditorAnimationCacheStatistics.recordMiss
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.paint.use
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.AlphaComposite
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.util.function.Supplier

private const val CACHE_ENABLED_REGISTRY_KEY = "editor.animation.cache.enabled"

/**
 * How much editor content the cache may hold, in multiples of the visible area.
 *
 * A zone spans the bounding box of everything a single repaint touches, so carets far apart produce zones as large
 * as the whole visible area. Once the budget is exhausted, the cache is dropped entirely rather than compacted:
 * the zone that is actually needed is rebuilt on the next animation tick.
 */
private const val MAX_CACHED_VISIBLE_AREAS = 2

internal class EditorAnimationCache private constructor(private val editor: EditorImpl) : Disposable {
  private var isDisposed = false
  private var lastCacheKey: Any? = null
  private var pixelGrid: PixelGrid? = null
  private val entries = CacheEntryList()

  @RequiresEdt
  fun clear() {
    lastCacheKey = null
    pixelGrid = null
    entries.clear()
  }

  @RequiresEdt
  override fun dispose() {
    isDisposed = true
    clear()
  }

  @RequiresEdt
  fun invalidate(clip: Rectangle?) {
    if (clip == null) {
      clear()
      return
    }
    lastCacheKey = null
    entries.removeIntersecting(clip)
  }

  /**
   * Caches the editor content behind the area that [rectangles] are about to be repainted in,
   * so that [paintFromCache] can restore it instead of repainting the content.
   *
   * A single zone covering the bounding box of [rectangles] is cached, not one zone per rectangle. Swing coalesces all
   * pending repaint requests for a component into their bounding box, so that box is the smallest clip
   * [paintFromCache] can ever be asked for. Caching the rectangles individually would leave the gaps between them
   * uncached, and no single zone would contain the clip, so every multi-caret repaint would miss.
   */
  @RequiresEdt
  fun cacheAreasForRepaint(key: Any, rectangles: Supplier<List<Rectangle2D>>) {
    if (isDisposed || editor.isDumb || lastCacheKey == key) return
    if (!ensureOpaqueContent()) return

    runCatching {
      val visibleArea = editor.scrollingModel.visibleArea
      if (visibleArea.isEmpty) return

      val currentPixelGrid = PixelGrid.forComponent(editor)
      if (pixelGrid != currentPixelGrid) {
        clear()
      }

      // The union is taken before clipping to the visible area, because that's the order Swing paints in:
      // it coalesces the repaint requests into their bounding box first, and only the resulting clip is visible-bound.
      val repaintedArea = rectangles.get()
        .reduceOrNull { union, rectangle -> union.createUnion(rectangle) }
        ?.intersectWithVisibleArea(visibleArea)
        ?.growToPixelGrid(currentPixelGrid)
        ?: return

      if (entries.findContaining(repaintedArea) == null) {
        if (entries.totalArea + repaintedArea.area > visibleArea.area * MAX_CACHED_VISIBLE_AREAS) {
          entries.clear()
        }
        val image = renderToImage(repaintedArea)
        // Building the cache paints editor content, which can run plugin code and dispose the view reentrantly.
        if (isDisposed) return
        entries.add(CacheEntry(repaintedArea, image))
        pixelGrid = currentPixelGrid
      }
      // Only remember the key once the zone is actually cached, so a transient failure doesn't skip every later
      // attempt: the caret key stays the same for a whole move, and giving up on it would leave the move uncached.
      lastCacheKey = key
    }.getOrHandleException { e ->
      LOG.error("An exception occurred while building editor animation cache", e)
    }
  }

  @RequiresEdt
  fun paintFromCache(graphics: Graphics2D, rect: Rectangle2D): Boolean {
    if (isDisposed || !ensureOpaqueContent()) return false
    val currentPixelGrid = PixelGrid.forGraphics(graphics)
    if (pixelGrid != currentPixelGrid) {
      clear()
      return false
    }
    val visibleRect = rect.visibleRectangle()?.growToPixelGrid(currentPixelGrid) ?: return false
    val entry = entries.findContaining(visibleRect)
    if (entry == null) return recordMiss()

    (graphics.create() as Graphics2D).use { frameGraphics ->
      frameGraphics.clip(visibleRect)
      frameGraphics.composite = AlphaComposite.Src
      entry.paint(frameGraphics)
      frameGraphics.composite = AlphaComposite.SrcOver
      editor.view.paintCaretFrame(frameGraphics)
    }
    return recordHit()
  }

  private fun ensureOpaqueContent(): Boolean {
    if (editor.contentComponent.isOpaque) return true
    clear()
    return false
  }

  private fun renderToImage(rectangle: Rectangle2D): BufferedImage {
    val image = createEditorImage(editor, rectangle.width, rectangle.height)
    editor.isCurrentlyBuildingCache = true
    try {
      createImageGraphics(editor, image, rectangle).use { graphics ->
        editor.paint(graphics)
      }
    }
    finally {
      editor.isCurrentlyBuildingCache = false
    }
    return image
  }

  private fun Rectangle2D.visibleRectangle(): Rectangle2D? {
    val visibleArea = editor.scrollingModel.visibleArea
    if (visibleArea.isEmpty) return null
    return intersectWithVisibleArea(visibleArea)?.coerceAtLeastEmpty()?.takeUnless { it.isEmpty }
  }

  private fun Rectangle2D.intersectWithVisibleArea(visibleArea: Rectangle): Rectangle2D? {
    if (!intersects(visibleArea)) return null
    return createIntersection(visibleArea)
  }

  private fun Rectangle2D.growToPixelGrid(pixelGrid: PixelGrid): Rectangle2D {
    val scaleContext = pixelGrid.scaleContext
    val alignment = pixelGrid.alignment
    val dx = alignment.x
    val dy = alignment.y
    val x0 = PaintUtil.alignToInt(dx + x, scaleContext, PaintUtil.RoundingMode.FLOOR, null)
    val y0 = PaintUtil.alignToInt(dy + y, scaleContext, PaintUtil.RoundingMode.FLOOR, null)
    val x1 = PaintUtil.alignToInt(dx + x + width, scaleContext, PaintUtil.RoundingMode.CEIL, null)
    val y1 = PaintUtil.alignToInt(dy + y + height, scaleContext, PaintUtil.RoundingMode.CEIL, null)
    // Now that we have everything aligned, shift back to the original misaligned space,
    // because that's the space that will be actually used for painting.
    return Rectangle2D.Double(x0 - dx, y0 - dy, x1 - x0, y1 - y0)
  }

  companion object {
    @JvmStatic
    fun createAnimationCache(editor: EditorImpl): EditorAnimationCache? =
      if (Registry.`is`(CACHE_ENABLED_REGISTRY_KEY)) EditorAnimationCache(editor) else null
  }
}

private data class PixelGrid(val scaleContext: ScaleContext, val alignment: Point2D) {
  companion object {
    fun forComponent(editor: EditorImpl): PixelGrid {
      val alignment = editor.contentComponent.currentAlignment
      return PixelGrid(
        ScaleContext.create(editor.contentComponent),
        Point2D.Double(alignment.x, alignment.y),
      )
    }

    fun forGraphics(graphics: Graphics2D): PixelGrid {
      val alignment = PaintUtil.getUserSpacePixelOffset(graphics) ?: Point2D.Double()
      return PixelGrid(
        ScaleContext.create(graphics),
        Point2D.Double(alignment.x, alignment.y),
      )
    }
  }
}

private val LOG = logger<EditorAnimationCache>()
