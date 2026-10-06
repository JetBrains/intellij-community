// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import com.intellij.ui.paint.use
import com.intellij.ui.scale.JBUIScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Canvas
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JRootPane
import javax.swing.SwingUtilities

internal class EditorSkeletonCanvas : Canvas(), EditorSkeletonRenderer {
  override val component: Canvas
    get() = this

  private val renderingStarted = AtomicBoolean()
  private val removedArea = AtomicReference<RemovedArea?>()
  private val backBuffer = EditorSkeletonBackBuffer()

  init {
    isFocusable = false
    ignoreRepaint = true
  }

  override fun removeNotify() {
    super.removeNotify()
    val rootPane = SwingUtilities.getRootPane(this) ?: return
    removedArea.set(RemovedArea(rootPane, SwingUtilities.convertRectangle(this, Rectangle(width, height), rootPane)))
  }

  override fun paint(g: Graphics) {}

  override fun update(g: Graphics) {}

  override fun startRendering(cs: CoroutineScope, paintFrame: (Graphics2D, Int, Int, Float) -> Unit) {
    if (!renderingStarted.compareAndSet(false, true)) return
    cs.launch(Dispatchers.Default) {
      try {
        while (isActive) {
          renderFrame(paintFrame)
          delay(EditorSkeletonRenderer.TICK_MS)
        }
      }
      finally {
        backBuffer.release()
        removedArea.set(null)
      }
    }
  }

  private fun renderFrame(paintFrame: (Graphics2D, Int, Int, Float) -> Unit) {
    val w = width
    val h = height
    val gc = graphicsConfiguration
    if (!isDisplayable || gc == null || w <= 0 || h <= 0) {
      backBuffer.release()
      return
    }

    val buffer = backBuffer.createOrUpdateImageBuffer(gc, w, h)
    buffer.createGraphics().use { g ->
      paintFrame(g, w, h, JBUIScale.scale(1f))
    }

    if (buffer.contentsLost()) return
    val screen = graphics ?: return
    screen.use { it.drawImage(buffer, 0, 0, null) }
    removedArea.getAndSet(null)?.repaintBounds()
  }

  private data class RemovedArea(private val rootPane: JRootPane, private val bounds: Rectangle) {
    fun repaintBounds() = rootPane.repaint(bounds)
  }
}
