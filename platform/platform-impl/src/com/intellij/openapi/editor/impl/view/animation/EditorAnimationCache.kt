// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorImageUtil.createEditorImage
import com.intellij.openapi.editor.impl.EditorImageUtil.createImageGraphics
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.caret.model.CARET_CACHE_RECTANGLE_MARGIN
import com.intellij.openapi.editor.impl.caret.model.CaretRectangle
import com.intellij.openapi.editor.impl.view.animation.EditorAnimationCacheStatistics.recordHit
import com.intellij.openapi.editor.impl.view.animation.EditorAnimationCacheStatistics.recordMiss
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.paint.use
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.AlphaComposite
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

internal class EditorAnimationCache(
  private val editor: EditorImpl,
) : Disposable {
  private val coroutineScope: CoroutineScope = editor.coroutineScope
  private val lastCacheKey = AtomicReference<EditorAnimationCacheKey?>(null)
  private val debugWindow: EditorAnimationCacheDebugWindow?

  /**
   * The zone that waits to be cached, or `null` when there is nothing to do. A newer request replaces an older one,
   * so that a stream of frames never builds a backlog.
   *
   * A [CacheRequest] carries the supplier that measures its own rectangles, so two requests are never equal even when
   * their keys match. [MutableStateFlow] drops a value equal to the current one without waking the collector, so that
   * inequality is what makes every posted request arrive.
   */
  private val requests = MutableStateFlow<CacheRequest?>(null)

  private var pixelGrid: EditorPixelGrid? = null
  private val entries = CacheEntryList()
  private var lastBuildAt: AnimationTimeMark? = null
  private var cooldownUntil: AnimationTimeMark? = null

  init {
    serveRequests()
    debugWindow = if (Registry.`is`("editor.animation.cache.debug.enabled", false)) {
      EditorAnimationCacheDebugWindow.createIfSupported(editor, entries)
    } else {
      null
    }
  }

  /**
   * Caches the editor content behind [locations], so that [paintFromCache] can restore it instead of repainting the
   * content. A request for content the cache already holds costs nothing beyond the key, which is the steady state of
   * a move: every frame of one move asks for the same zone.
   *
   * A single zone covering the bounding box of [locations] is cached, not one zone per caret. Swing coalesces all
   * pending repaint requests for a component into their bounding box, so that box is the smallest clip
   * [paintFromCache] can ever be asked for. Caching the carets individually would leave the gaps between them
   * uncached, and no single zone would contain the clip, so every multi-caret repaint would miss.
   */
  fun cacheCaretFrames(locations: List<CaretRectangle>) {
    val requestKey = EditorAnimationCacheKey.of(locations)
    val cachedKey = lastCacheKey.get()
    if (requestKey == cachedKey) {
      // Also drop whatever waits here, because the carets have moved past it.
      requests.value = null
      return
    }
    requests.update { pending: CacheRequest? ->
      if (pending?.isDuplicate(requestKey) == true) {
        pending
      } else {
        CacheRequest(requestKey) {
          caretCacheRectangles(locations)
        }
      }
    }
  }

  /**
   * Paints the cached content behind [rect] plus the current caret frame, instead of repainting the editor.
   * Returns `false` when nothing usable is cached, so the caller has to repaint after all.
   */
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun paintFromCache(graphics: Graphics2D, rect: Rectangle2D): Boolean {
    if (editor.isDisposed || !ensureOpaqueContent()) {
      return false
    }
    val currentPixelGrid = EditorPixelGrid.forGraphics(graphics)
    if (pixelGrid != currentPixelGrid) {
      clear()
      return false
    }
    val visiblePart = rect.visibleRectangle() ?: return false
    val visibleRect = currentPixelGrid.align(visiblePart)
    val entry = entries.findContaining(visibleRect)
    if (entry == null) {
      return recordMiss()
    }
    (graphics.create() as Graphics2D).use { frameGraphics ->
      frameGraphics.clip(visibleRect)
      frameGraphics.composite = AlphaComposite.Src
      entry.paint(frameGraphics)
      frameGraphics.composite = AlphaComposite.SrcOver
      editor.view.paintCaretFrame(frameGraphics)
    }
    debugWindow?.servedAreaChanged(visibleRect)
    return recordHit()
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun clear() {
    lastCacheKey.set(null)
    pixelGrid = null
    entries.clear()
    debugWindow?.zonesChanged()
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  fun invalidate(clip: Rectangle?) {
    if (clip == null) {
      clear()
      return
    }
    lastCacheKey.set(null)
    // Nothing was ever built, so there is no entry to drop and no thrashing to detect.
    val lastBuildAt = lastBuildAt ?: return
    val now = AnimationClock.markAnimationNow()
    val removedEntries = entries.removeIntersecting(clip)
    if (removedEntries) {
      debugWindow?.zonesChanged()
    }
    val builtRecently = (now - lastBuildAt) < THRASH_WINDOW
    if (removedEntries && builtRecently) {
      cooldownUntil = now + THRASH_COOLDOWN
    }
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  override fun dispose() {
    requests.value = null
    clear()
  }

  private fun serveRequests() {
    coroutineScope.launch(DISPATCHER) {
      requests.filterNotNull().collect { request: CacheRequest ->
        serve(request)
      }
    }
  }

  private suspend fun serve(request: CacheRequest) {
    requests.compareAndSet(request, null)
    // [cacheCaretFrames] drops a request for content the cache holds. This catches the narrower case of a key that
    // was stored while the request waited here.
    if (request.isDuplicate(lastCacheKey.get())) {
      return
    }
    withContext(Dispatchers.UI + ModalityState.any().asContextElement()) {
      cacheMissingAreas(request)
    }
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  private fun cacheMissingAreas(request: CacheRequest) {
    if (editor.isDisposed || editor.isDumb) {
      return
    }
    if (isWithinCooldown()) {
      return
    }
    if (!ensureOpaqueContent()) {
      return
    }
    runCatching {
      cacheRequestedArea(request)
    }.getOrHandleException { e ->
      LOG.error("An exception occurred while building editor animation cache", e)
    }
  }

  /**
   * The areas that [locations] need cached. Measured when the request reaches the EDT, so that the geometry belongs to
   * the frame the cache is actually built for.
   */
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  private fun caretCacheRectangles(locations: List<CaretRectangle>): List<Rectangle2D> {
    val caretRectangles = editor.view.caretRectanglesForLocations(locations.toTypedArray(), CARET_CACHE_RECTANGLE_MARGIN)
    return caretRectangles.map { rectangle -> rectangle.coerceAtLeastEmpty() }
  }

  private fun isWithinCooldown(): Boolean {
    val cooldownUntil = cooldownUntil ?: return false
    return AnimationClock.markAnimationNow() < cooldownUntil
  }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  private fun cacheRequestedArea(request: CacheRequest) {
    val visibleArea = editor.scrollingModel.visibleArea
    if (visibleArea.isEmpty) {
      return
    }
    val currentPixelGrid = EditorPixelGrid.forComponent(editor)
    if (pixelGrid != currentPixelGrid) {
      clear()
    }
    val repaintedArea = request.repaintedArea(visibleArea, currentPixelGrid) ?: return
    val alreadyCached = entries.findContaining(repaintedArea) != null
    if (!alreadyCached) {
      val built = buildEntry(repaintedArea, currentPixelGrid, visibleArea)
      if (!built) {
        return
      }
    }
    // Only remember the key once the zone is actually cached, so a transient failure doesn't skip every later
    // attempt: the caret key stays the same for a whole move, and giving up on it would leave the move uncached.
    request.storeKeyIn(lastCacheKey)
  }

  /**
   * Renders and stores the content behind [repaintedArea]. Returns `false` when the view was disposed while painting.
   */
  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  private fun buildEntry(repaintedArea: Rectangle2D, grid: EditorPixelGrid, visibleArea: Rectangle): Boolean {
    val image = renderToImage(repaintedArea)
    // Building the cache paints editor content, which can run plugin code and dispose the view reentrantly.
    if (editor.isDisposed) {
      return false
    }
    val budget = visibleArea.area() * MAX_CACHED_VISIBLE_AREAS
    entries.add(CacheEntry(repaintedArea, image), budget)
    debugWindow?.zonesChanged()
    pixelGrid = grid
    lastBuildAt = AnimationClock.markAnimationNow()
    return true
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

  private fun ensureOpaqueContent(): Boolean {
    if (editor.contentComponent.isOpaque) {
      return true
    }
    clear()
    return false
  }

  private fun Rectangle2D.visibleRectangle(): Rectangle2D? {
    val visibleArea = editor.scrollingModel.visibleArea
    if (visibleArea.isEmpty) {
      return null
    }
    val visiblePart = intersectWithVisibleArea(visibleArea) ?: return null
    val clampedPart = visiblePart.coerceAtLeastEmpty()
    if (clampedPart.isEmpty) {
      return null
    }
    return clampedPart
  }

  companion object {
    private val LOG = logger<EditorAnimationCache>()

    private val DISPATCHER = Dispatchers.Default.limitedParallelism(1, "EditorAnimationCache")

    /**
     * How much editor content the cache may hold, in multiples of the visible area.
     *
     * A zone spans the bounding box of everything a single repaint touches, so carets far apart produce zones as large
     * as the whole visible area. Once the budget is exhausted, the cache is dropped entirely rather than compacted:
     * the zone that is actually needed is rebuilt on the next animation tick.
     */
    private const val MAX_CACHED_VISIBLE_AREAS = 2

    /**
     * A cache rebuilt within [THRASH_WINDOW] of the previous build is treated as thrashing, and requests are dropped
     * for [THRASH_COOLDOWN] so that a stream of invalidations does not keep repainting content nobody gets to reuse.
     */
    private val THRASH_WINDOW = 100.milliseconds

    private val THRASH_COOLDOWN = 250.milliseconds
  }
}
