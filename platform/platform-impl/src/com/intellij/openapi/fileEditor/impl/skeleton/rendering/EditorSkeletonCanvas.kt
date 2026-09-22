// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.skeleton.rendering

import com.intellij.ui.scale.JBUIScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Canvas
import java.awt.Graphics
import java.awt.Graphics2D
import java.util.concurrent.atomic.AtomicBoolean

internal class EditorSkeletonCanvas : Canvas(), EditorSkeletonRenderer {
  override val component: Canvas
    get() = this

  private val renderingStarted = AtomicBoolean()

  init {
    isFocusable = false
    ignoreRepaint = true
  }

  override fun removeNotify() {
    synchronized(treeLock) {
      bufferStrategy?.dispose()
      super.removeNotify()
    }
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
        synchronized(treeLock) {
          bufferStrategy?.dispose()
        }
      }
    }
  }

  private fun renderFrame(paintFrame: (Graphics2D, Int, Int, Float) -> Unit) {
    synchronized(treeLock) {
      val w = width
      val h = height
      if (!isDisplayable || w <= 0 || h <= 0) return
      val buffer = bufferStrategy ?: run {
        createBufferStrategy(2)
        bufferStrategy
      }
      val graphics = buffer.drawGraphics as Graphics2D
      try {
        paintFrame(graphics, w, h, JBUIScale.scale(1f))
      }
      finally {
        graphics.dispose()
      }
      if (!buffer.contentsRestored()) {
        buffer.show()
      }
    }
  }
}
