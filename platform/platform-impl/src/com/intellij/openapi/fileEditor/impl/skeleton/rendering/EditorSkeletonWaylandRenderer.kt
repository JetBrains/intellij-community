// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import com.intellij.openapi.application.UI
import com.intellij.openapi.fileEditor.impl.skeleton.EditorSkeleton
import com.intellij.ui.scale.JBUIScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JComponent

/**
 * Paints on the EDT so Swing submits each painted frame to Wayland.
 *
 * 1. `WLToolkit.createCanvas()` delegates to `LWToolkit.createCanvas()`, which creates an `LWCanvasPeer`.
 *    This peer inherits `LWComponentPeer.flip()`.
 *
 *    ```java
 *    // WLToolkit.createCanvas(target)
 *    return LWToolkit.createCanvas(target, LWDummyPlatformComponent.getInstance());
 *    // LWToolkit.createCanvas(target, platformComponent)
 *    LWCanvasPeer<?, ?> peer = new LWCanvasPeer<>(target, platformComponent);
 *    ```
 *
 * 2. `Canvas.createBufferStrategy(2)` selects `Component.FlipSubRegionBufferStrategy` when buffer creation succeeds.
 *    It inherits `FlipBufferStrategy.show()`, which calls `flip()` and then the peer.
 *
 *    ```java
 *    // Component.FlipBufferStrategy.show()
 *    flip(caps.getFlipContents());
 *    // Component.FlipBufferStrategy.flip(flipAction)
 *    peer.flip(0, 0, backBuffer.getWidth(null), backBuffer.getHeight(null), flipAction);
 *    ```
 *
 * 3. `LWComponentPeer.flip()` delegates to the graphics configuration.
 *    `WLGraphicsConfig` implements `LWGraphicsConfig` and inherits its `flip()` method.
 *
 *    ```java
 *    // LWComponentPeer.flip(x1, y1, x2, y2, flipAction)
 *    getLWGC().flip(this, getBackBuffer(), x1, y1, x2, y2, flipAction);
 *    ```
 *
 * 4. `LWGraphicsConfig.flip()` copies the back buffer into the window surface through the peer's graphics.
 *    It does not call `WLComponentPeer.commitToServer()`. The pixels can therefore wait for another window update.
 *
 *    ```java
 *    // LWGraphicsConfig.flip(peer, backBuffer, x1, y1, x2, y2, flipAction)
 *    final Graphics g = peer.getGraphics();
 *    g.drawImage(backBuffer, x1, y1, x2, y2, x1, y1, x2, y2, null);
 *    g.dispose();
 *    ```
 *
 * This renderer uses `repaint()` instead. After painting, `RepaintManager.updateWindows()` follows this chain:
 *
 * ```text
 * RepaintManager.paintDirtyRegions() -> updateWindows()
 * -> AWTAccessor.getWindowAccessor().updateWindow(window)
 * -> Window.updateWindow() -> WLWindowPeer.updateWindow()
 * -> WLComponentPeer.commitToServer()
 * ```
 *
 * Wayland enables both `needUpdateWindow()` and `needUpdateWindowAfterPaint()`, so this includes opaque windows.
 * `WLWindowPeer.updateWindow()` calls `WLComponentPeer.commitToServer()`, which commits the surface and flushes the Wayland connection:
 *
 * ```java
 * // WLComponentPeer.commitToServer(), with a valid surface under the peer lock
 * SurfaceData.convertTo(WLSurfaceDataExt.class, surfaceData).commit();
 * // After releasing the peer lock
 * ((WLToolkit) Toolkit.getDefaultToolkit()).flush();
 * ```
 */
internal class EditorSkeletonWaylandRenderer : JComponent(), EditorSkeletonRenderer {
  override val component: JComponent
    get() = this

  private var scope: CoroutineScope? = null
  private var renderingJob: Job? = null
  private var paintFrame: ((Graphics2D, Int, Int, Float) -> Unit)? = null
  private var lastFrameTimeMs: Long? = null
  private var animationTimeMs = 0L

  init {
    isFocusable = false
    isOpaque = true
  }

  override fun startRendering(cs: CoroutineScope, paintFrame: (Graphics2D, Int, Int, Float) -> Unit) {
    if (scope != null) return
    scope = cs
    this.paintFrame = paintFrame
    if (isDisplayable) startRepainting()
  }

  override fun addNotify() {
    super.addNotify()
    startRepainting()
  }

  override fun removeNotify() {
    renderingJob?.cancel()
    renderingJob = null
    lastFrameTimeMs = null
    super.removeNotify()
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    val paintFrame = paintFrame
    if (paintFrame == null || !isShowing) {
      g.color = EditorSkeleton.EDITOR_BACKGROUND_COLOR
      g.fillRect(0, 0, width, height)
      return
    }
    paintFrame(g as Graphics2D, width, height, JBUIScale.scale(1f))
  }

  override fun frameTimeMs(nowMs: Long): Long {
    val previousFrameTimeMs = lastFrameTimeMs
    if (previousFrameTimeMs != null) {
      val elapsedMs = nowMs - previousFrameTimeMs
      if (elapsedMs in 0L..MAX_FRAME_GAP_MS) {
        animationTimeMs += elapsedMs
      }
    }
    lastFrameTimeMs = nowMs
    return animationTimeMs
  }

  private fun startRepainting() {
    val cs = scope ?: return
    if (renderingJob != null) return
    renderingJob = cs.launch(Dispatchers.UI) {
      while (isActive) {
        if (isShowing) repaint()
        delay(EditorSkeletonRenderer.TICK_MS)
      }
    }
  }

  private companion object {
    const val MAX_FRAME_GAP_MS = 50L
  }
}
