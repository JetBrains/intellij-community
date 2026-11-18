// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import com.intellij.openapi.application.UI
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
 * Paints the skeleton on the EDT with Swing `repaint()`. It does not use a heavyweight `java.awt.Canvas`.
 *
 * ## Wayland
 *
 * Swing submits each painted frame to Wayland. A `Canvas` buffer strategy does not do this.
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
 *
 * ## Windows
 *
 * This renderer creates no `Canvas`, so it avoids JBR-10643
 */
internal class EditorSkeletonRepaintRenderer(private val frames: EditorSkeletonFrames) : JComponent(), EditorSkeletonRenderer {
  override val component: JComponent
    get() = this

  private var scope: CoroutineScope? = null
  private var renderingJob: Job? = null
  private var lastFrameTimeMs: Long? = null
  private var animationClockMs = 0L

  init {
    isFocusable = false
    isOpaque = true
  }

  override fun startRendering(cs: CoroutineScope) {
    if (scope != null) return
    scope = cs
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
    frames.paint(g as Graphics2D, width, height, JBUIScale.scale(1f), frames.colorsAt(animationTimeMs()))
  }

  /**
   * Returns the time on the clock of the animation. A gap of more than [MAX_FRAME_GAP_MS] between two frames does not count,
   * so the animation does not jump after the EDT was busy.
   */
  private fun animationTimeMs(): Long {
    val nowMs = System.currentTimeMillis()
    val previousFrameTimeMs = lastFrameTimeMs
    if (previousFrameTimeMs != null) {
      val elapsedMs = nowMs - previousFrameTimeMs
      if (elapsedMs in 0L..MAX_FRAME_GAP_MS) {
        animationClockMs += elapsedMs
      }
    }
    lastFrameTimeMs = nowMs
    return animationClockMs
  }

  private fun startRepainting() {
    val cs = scope ?: return
    if (renderingJob != null) return
    renderingJob = cs.launch(Dispatchers.UI) {
      var repaintedColors: SkeletonFrameColor? = null
      while (isActive) {
        if (isShowing) {
          val colors = frames.colorsAt(animationTimeMs())
          if (colors != repaintedColors) {
            repaintedColors = colors
            repaint()
          }
        }
        delay(EditorSkeletonRenderer.TICK_MS)
      }
    }
  }

  private companion object {
    const val MAX_FRAME_GAP_MS = 50L
  }
}
