// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.drag

import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.util.JBHiDPIScaledImage
import com.intellij.util.ui.StartupUiUtil
import java.awt.*
import javax.swing.JLabel
import javax.swing.SwingUtilities

/**
 * A glass pane based [DragImageView] implementation.
 *
 * Intended to be used on Wayland, where it's impossible to move a window,
 * and therefore using an undecorated dialog doesn't work.
 * Of course, this implementation is limited only to the main window,
 * but because it's impossible to drag anything outside the main window anyway,
 * it's good enough for Wayland.
 */
internal class GlassPaneDragImageView(private val glassPane: IdeGlassPane) : DragImageView {
  private val glassPaneComponent = glassPane as? Component
  private val disposable = Disposer.newDisposable()
  private val painter = DragImagePainter()
  private val background = JLabel().background

  override var location: Point = Point(0, 0)
    set(value) {
      field = value
      repaint()
    }

  override val bounds: Rectangle
    get() = Rectangle(location, size)

  override val size: Dimension
    get() = preferredSize

  override val preferredSize: Dimension
    get() = image?.let { Dimension(it.getWidth(), it.getHeight()) } ?: Dimension(0, 0)

  private fun Image.getWidth(): Int = when (this) {
    is JBHiDPIScaledImage -> getUserWidth()
    else -> getWidth(glassPaneComponent)
  }

  private fun Image.getHeight(): Int = when (this) {
    is JBHiDPIScaledImage -> getUserHeight()
    else -> getHeight(glassPaneComponent)
  }

  override var image: Image? = null
    set(value) {
      field = value
      repaint()
    }

  private fun repaint() {
    glassPaneComponent?.repaint()
  }

  override fun show() {
    glassPane.addPainter(null, painter, disposable)
  }

  override fun hide() {
    Disposer.dispose(disposable)
  }

  private inner class DragImagePainter : AbstractPainter() {
    override fun needsRepaint(): Boolean = true

    override fun executePaint(component: Component, g: Graphics2D) {
      val image = image ?: return
      val g2 = g.create()
      try {
        g2.color = background
        val size = size
        val location = Point(location)
        SwingUtilities.convertPointFromScreen(location, component)
        g2.fillRect(location.x, location.y, size.width, size.height)
        StartupUiUtil.drawImage(g2, image, location.x, location.y, observer = component)
      }
      finally {
        g2.dispose()
      }
    }
  }
}
