package com.intellij.openapi.wm.impl

import org.jetbrains.annotations.ApiStatus
import java.awt.AlphaComposite
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Shape
import java.awt.Window
import javax.swing.JPanel
import javax.swing.RootPaneContainer

@ApiStatus.Internal
class MacWindowMask(content: Component?) : JPanel(BorderLayout()) {
  var mask: Shape? = null
    set(value) {
      field = value
      repaint()
    }
  private var backgroundSaved = false
  private var originalBackground: Color? = null

  init {
    if (content != null) add(content, BorderLayout.CENTER)
  }

  override fun paint(graphics: Graphics) {
    val clearGraphics = graphics.create() as Graphics2D
    try {
      clearGraphics.composite = AlphaComposite.Clear
      clearGraphics.fillRect(0, 0, width, height)
    }
    finally {
      clearGraphics.dispose()
    }
    val contentGraphics = graphics.create() as Graphics2D
    try {
      mask?.let { contentGraphics.clip(it) }
      super.paint(contentGraphics)
    }
    finally {
      contentGraphics.dispose()
    }
  }

  fun apply(window: Window, shape: Shape?) {
    mask = shape
    if (shape != null) {
      if (!backgroundSaved) {
        originalBackground = window.background
        backgroundSaved = true
      }
      window.background = Color(0, 0, 0, 0)
    }
    else if (backgroundSaved) {
      window.background = originalBackground?.let { Color(it.rgb, true) }
      originalBackground = null
      backgroundSaved = false
    }
    val rootPane = (window as? RootPaneContainer)?.rootPane
    if (rootPane != null && rootPane.getClientProperty("apple.awt.draggableWindowBackground") == null) {
      rootPane.putClientProperty("apple.awt.draggableWindowBackground", false)
    }
  }
}
