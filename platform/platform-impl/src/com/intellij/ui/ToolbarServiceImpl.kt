// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.ui.mac.MacSystemTitleBarBorder
import com.intellij.ui.mac.MacWindowTitleBar
import com.intellij.ui.paint.useCopy
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import java.awt.Window
import java.beans.PropertyChangeListener
import javax.swing.JFrame
import javax.swing.JRootPane
import javax.swing.UIManager
import javax.swing.border.AbstractBorder

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

    if (window is JFrame && window !is IdeFrameImpl) {
      MacWindowTitleBar(frame = window, rootPane = rootPane, fullScreenSupport = handler).install(onDispose)
    }
    else {
      rootPane.border = MacSystemTitleBarBorder(window) { handler != null && handler.isFullScreen() }
    }
    doSetCustomTitleBar(window = window, rootPane = rootPane, onDispose = onDispose)
  }
}

private fun setCustomTitleForToolbar(window: Window, rootPane: JRootPane, onDispose: (Runnable) -> Unit) {
  val topWindowInset = JBUI.insetsTop(UIUtil.getTransparentTitleBarHeight(rootPane))
  val customBorder = object : AbstractBorder() {
    override fun getBorderInsets(c: Component): Insets = topWindowInset

    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
      g.useCopy { graphics ->
        val headerRectangle = Rectangle(0, 0, c.width, topWindowInset.top)
        val color = UIManager.getColor("MainToolbar.background")
        graphics.color = color ?: UIUtil.getPanelBackground()
        graphics.fill(headerRectangle)
      }
    }
  }
  rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
  rootPane.border = customBorder
  doSetCustomTitleBar(window = window, rootPane = rootPane, onDispose = onDispose)
}

private fun doSetCustomTitleBar(window: Window, rootPane: JRootPane, onDispose: (Runnable) -> Unit) {
  rootPane.putClientProperty("apple.awt.fullWindowContent", true)
  rootPane.putClientProperty("apple.awt.transparentTitleBar", true)

  val propertyChangeListener = PropertyChangeListener { rootPane.repaint() }
  window.addPropertyChangeListener("title", propertyChangeListener)
  onDispose { window.removePropertyChangeListener("title", propertyChangeListener) }
}
