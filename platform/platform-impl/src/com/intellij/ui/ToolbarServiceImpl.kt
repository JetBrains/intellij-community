// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.ui.paint.LinePainter2D
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.RootPaneContainer
import javax.swing.UIManager
import javax.swing.border.AbstractBorder
import javax.swing.border.Border

@ApiStatus.Internal
open class ToolbarServiceImpl : ToolbarService {
  override fun setCustomTitleBar(window: Window, rootPane: JRootPane, onDispose: (Runnable) -> Unit) {
    if (!SystemInfoRt.isMac) {
      return
    }
    else if (ExperimentalUI.isNewUI()) {
      setCustomTitleForToolbar(window = window, rootPane = rootPane, onDispose = onDispose)
    }
    else {
      setTransparentTitleBar(window = window, rootPane = rootPane, onDispose = onDispose)
    }
  }

  override fun setTransparentTitleBar(window: Window,
                                      rootPane: JRootPane,
                                      handlerProvider: (() -> FullScreenSupport)?,
                                      onDispose: (Runnable) -> Unit) {
    if (!SystemInfoRt.isMac) {
      return
    }

    val handler = handlerProvider?.invoke()
    handler?.addListener(window)

    // native window title bar doesn't scale
    @Suppress("UseDPIAwareInsets") val topWindowInset = Insets(UIUtil.getTransparentTitleBarHeight(rootPane), 0, 0, 0)
    val customBorder = object : AbstractBorder() {
      override fun getBorderInsets(c: Component): Insets {
        return if (handler != null && handler.isFullScreen()) JBInsets.emptyInsets() else topWindowInset
      }

      override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
        if (handler != null && handler.isFullScreen()) {
          return
        }

        val graphics = (g.create() as Graphics2D).let {
          if (c is JComponent) JBSwingUtilities.runGlobalCGTransform(c, it) else it
        }

        try {
          val headerRectangle = Rectangle(0, 0, c.width, topWindowInset.top)
          graphics.color = JBColor.PanelBackground
          graphics.fill(headerRectangle)
          if (window is RootPaneContainer) {
            val pane = (window as RootPaneContainer).rootPane
            if (pane == null || pane.getClientProperty(UIUtil.NO_BORDER_UNDER_WINDOW_TITLE_KEY) == false) {
              graphics.color = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
              LinePainter2D.paint(graphics, 0.0, (topWindowInset.top - 1).toDouble(), c.width.toDouble(),
                                  (topWindowInset.top - 1).toDouble(),
                                  LinePainter2D.StrokeType.INSIDE, 1.0)
            }
          }
          val color = if (window.isActive) JBColor.black else JBColor.gray
          graphics.color = color
        }
        finally {
          graphics.dispose()
        }
      }
    }

    @Suppress("NAME_SHADOWING")
    var onDispose = onDispose
    if (handler != null) {
      val onDisposeOld = onDispose
      onDispose = { runnable ->
        onDisposeOld {
          runnable.run()
          handler.removeListener(window)
        }
      }
    }
    doSetCustomTitleBar(window = window, rootPane = rootPane, onDispose = onDispose, customBorder = customBorder)
  }
}

private fun setCustomTitleForToolbar(window: Window, rootPane: JRootPane, onDispose: (Runnable) -> Unit) {
  val topWindowInset = JBUI.insetsTop(UIUtil.getTransparentTitleBarHeight(rootPane))
  val customBorder = object : AbstractBorder() {
    override fun getBorderInsets(c: Component): Insets = topWindowInset

    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
      val graphics = g.create() as Graphics2D
      try {
        val headerRectangle = Rectangle(0, 0, c.width, topWindowInset.top)
        val color = UIManager.getColor("MainToolbar.background")
        graphics.color = color ?: UIUtil.getPanelBackground()
        graphics.fill(headerRectangle)
      }
      finally {
        graphics.dispose()
      }
    }
  }
  rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
  doSetCustomTitleBar(window = window, rootPane = rootPane, onDispose = onDispose, customBorder = customBorder)
}

private fun doSetCustomTitleBar(window: Window, rootPane: JRootPane, onDispose: (Runnable) -> Unit, customBorder: Border) {
  rootPane.putClientProperty("apple.awt.fullWindowContent", true)
  rootPane.putClientProperty("apple.awt.transparentTitleBar", true)

  rootPane.setBorder(customBorder)
  val propertyChangeListener = PropertyChangeListener { rootPane.repaint() }
  window.addPropertyChangeListener("title", propertyChangeListener)
  onDispose { window.removePropertyChangeListener("title", propertyChangeListener) }
}
