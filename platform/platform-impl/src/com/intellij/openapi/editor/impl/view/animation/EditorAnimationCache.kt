// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorImageUtil.createEditorImage
import com.intellij.openapi.editor.impl.EditorImageUtil.createImageGraphics
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.animation.EditorAnimationCacheStatistics.recordHit
import com.intellij.openapi.editor.impl.view.animation.EditorAnimationCacheStatistics.recordMiss
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.paint.use
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.AlphaComposite
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

private const val CACHE_ENABLED_REGISTRY_KEY = "editor.animation.cache.enabled"

/**
 * How much editor content the cache may hold, in multiples of the visible area.
 *
 * A zone spans the bounding box of everything a single repaint touches, so carets far apart produce zones as large
 * as the whole visible area. Once the budget is exhausted, the cache is dropped entirely rather than compacted:
 * the zone that is actually needed is rebuilt on the next animation tick.
 */
private const val MAX_CACHED_VISIBLE_AREAS = 2

private val THRASH_WINDOW_MS = 100.milliseconds
private val THRASH_COOLDOWN_MS = 250.milliseconds

@Service(Service.Level.APP)
internal class EditorAnimationCacheService(private val scope: CoroutineScope) {
  private val dispatcher = Dispatchers.Default.limitedParallelism(1, "EditorAnimationCache")

  fun createCache(editor: EditorImpl): EditorAnimationCache =
    EditorAnimationCache(editor, scope.childScope("Editor animation cache", dispatcher))
}

internal class EditorAnimationCache(
  private val editor: EditorImpl,
  private val coroutineScope: CoroutineScope,
) : Disposable {
  private var isDisposed = false
  private val lastCacheKey = AtomicReference<EditorAnimationCacheKey?>(null)
  private val requests = MutableStateFlow<CacheRequest?>(null)
  private var pixelGrid: EditorPixelGrid? = null
  private val entries = CacheEntryList()
  private var lastBuildAt: AnimationTimeMark? = null
  private var cooldownUntil: AnimationTimeMark? = null

  fun start() = coroutineScope.launch {
    requests.filterNotNull().collect { request ->
      requests.compareAndSet(request, null)
      if (request.isDuplicate(lastCacheKey.get())) return@collect

      withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
        cacheMissingAreas(request)
      }
    }
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun clear() {
    lastCacheKey.set(null)
    pixelGrid = null
    entries.clear()
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  override fun dispose() {
    isDisposed = true
    coroutineScope.cancel()
    requests.value = null
    clear()
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun invalidate(clip: Rectangle?) {
    if (clip == null) {
      clear()
      return
    }
    lastCacheKey.set(null)

    val lastBuildAt = lastBuildAt ?: return
    val now = AnimationClock.markAnimationNow()
    if (entries.removeIntersecting(clip) && (now - lastBuildAt) < THRASH_WINDOW_MS) {
      cooldownUntil = now + THRASH_COOLDOWN_MS
    }
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
  fun cacheAreasForRepaint(requestKey: EditorAnimationCacheKey, rectangles: Supplier<List<Rectangle2D>>) = requests.update { pending ->
    when (pending?.isDuplicate(requestKey)) {
      true -> pending
      else -> CacheRequest(requestKey, rectangles)
    }
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  private fun cacheMissingAreas(request: CacheRequest) {
    if (isDisposed || editor.isDumb) return

    val shouldSkipRequest = when (val cooldownUntil = cooldownUntil) {
      null -> false
      else -> AnimationClock.markAnimationNow() < cooldownUntil
    }
    if (shouldSkipRequest) return
    if (!ensureOpaqueContent()) return

    runCatching {
      val visibleArea = editor.scrollingModel.visibleArea
      if (visibleArea.isEmpty) return

      val currentPixelGrid = EditorPixelGrid.forComponent(editor)
      if (pixelGrid != currentPixelGrid) {
        clear()
      }

      // The union is taken before clipping to the visible area, because that's the order Swing paints in:
      // it coalesces the repaint requests into their bounding box first, and only the resulting clip is visible-bound.
      val repaintedArea = request.repaintedArea(visibleArea, currentPixelGrid) ?: return

      if (entries.findContaining(repaintedArea) == null) {
        if (entries.totalArea + repaintedArea.area > visibleArea.area * MAX_CACHED_VISIBLE_AREAS) {
          entries.clear()
        }
        val image = renderToImage(repaintedArea)
        // Building the cache paints editor content, which can run plugin code and dispose the view reentrantly.
        if (isDisposed) return
        entries.add(CacheEntry(repaintedArea, image))
        pixelGrid = currentPixelGrid
        lastBuildAt = AnimationClock.markAnimationNow()
      }
      // Only remember the key once the zone is actually cached, so a transient failure doesn't skip every later
      // attempt: the caret key stays the same for a whole move, and giving up on it would leave the move uncached.
      request.pushKey(lastCacheKey)
    }.getOrHandleException { e ->
      LOG.error("An exception occurred while building editor animation cache", e)
    }
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun paintFromCache(graphics: Graphics2D, rect: Rectangle2D): Boolean {
    if (isDisposed || !ensureOpaqueContent()) return false
    val currentPixelGrid = EditorPixelGrid.forGraphics(graphics)
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

  companion object {
    @JvmStatic
    fun createAnimationCache(editor: EditorImpl): EditorAnimationCache? {
      if (!Registry.`is`(CACHE_ENABLED_REGISTRY_KEY)) return null
      return service<EditorAnimationCacheService>().createCache(editor)
    }
  }
}


private val LOG = logger<EditorAnimationCache>()
