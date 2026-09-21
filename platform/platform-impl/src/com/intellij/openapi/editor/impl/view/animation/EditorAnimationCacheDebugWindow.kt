// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view.animation

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.platform.jbr.JdkEx
import com.intellij.ui.JBColor
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.geom.AffineTransform
import java.awt.geom.Area
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.JWindow
import javax.swing.SwingUtilities

/**
 * Mirrors the zones of one animation cache in a window above the editor.
 *
 * The window ignores every mouse event, so the editor receives them all and stays fully usable. A repaint of the
 * window reaches no component of the editor, so it cannot run [EditorPainterCache.invalidate]. The marks therefore
 * show the cache as it is, and they need nothing from the paint cycle of the editor.
 */
internal class EditorAnimationCacheDebugWindow(
  private val editor: EditorImpl,
  private val entries: CacheEntryList,
) : Disposable {
  private val marks = CacheMarks()
  private var window: JWindow? = null
  private var servedArea: Rectangle2D? = null
  private val onEditorScrolled = VisibleAreaListener {
    refresh()
  }
  private val onFrameMoved = object : ComponentAdapter() {
    override fun componentMoved(event: ComponentEvent) {
      refresh()
    }
    override fun componentResized(event: ComponentEvent) {
      refresh()
    }
  }
  private val whileShowing: Job = editor.contentComponent.launchOnShow(WINDOW_NAME) {
    try {
      openWindow()
      awaitCancellation()
    }
    finally {
      closeWindow()
    }
  }

  /**
   * Redraws the marks, because the cache dropped or built a zone.
   */
  fun zonesChanged() {
    refresh()
  }

  /**
   * Redraws the marks, because another frame came from the cache.
   */
  fun servedAreaChanged(area: Rectangle2D) {
    servedArea = area
    refresh()
  }

  override fun dispose() {
    whileShowing.cancel()
    closeWindow()
  }

  private fun openWindow() {
    val owner = SwingUtilities.getWindowAncestor(editor.contentComponent) ?: return
    val opened = JWindow(owner)
    opened.contentPane = marks
    // The same recipe as HwFacadeHelper, which also holds a transparent window above IDE content. The window ignores
    // every mouse event, so the editor keeps them all, and a transparent background leaves only the borders visible.
    JdkEx.setIgnoreMouseEvents(opened, true)
    opened.focusableWindowState = false
    JdkEx.setTransparent(opened)
    window = opened
    owner.addComponentListener(onFrameMoved)
    editor.scrollingModel.addVisibleAreaListener(onEditorScrolled)
    refresh()
  }

  private fun closeWindow() {
    val opened = window ?: return
    window = null
    editor.scrollingModel.removeVisibleAreaListener(onEditorScrolled)
    opened.owner?.removeComponentListener(onFrameMoved)
    opened.isVisible = false
    opened.dispose()
  }

  /**
   * Moves the window over the viewport and hands it the borders it has to draw.
   */
  private fun refresh() {
    val opened = window ?: return
    val contentComponent = editor.contentComponent
    if (!contentComponent.isShowing) {
      opened.isVisible = false
      return
    }
    val visibleArea = editor.scrollingModel.visibleArea
    if (visibleArea.isEmpty) {
      opened.isVisible = false
      return
    }
    val zoneBorders = entries.borderShape(visibleArea)
    if (zoneBorders.isEmpty) {
      // Nothing of the cache is on screen, so the area served last is out of sight as well.
      opened.isVisible = false
      return
    }
    // The window covers the viewport, so a border moves by however far the editor is scrolled.
    val intoWindow = AffineTransform.getTranslateInstance(-visibleArea.x.toDouble(), -visibleArea.y.toDouble())
    val servedBorder = servedBorderIn(visibleArea)
    zoneBorders.transform(intoWindow)
    servedBorder?.transform(intoWindow)
    // The origin of the content component sits above the viewport by however far the editor is scrolled.
    val componentOrigin = contentComponent.locationOnScreen
    opened.setBounds(componentOrigin.x + visibleArea.x, componentOrigin.y + visibleArea.y, visibleArea.width, visibleArea.height)
    marks.showBorders(zoneBorders, servedBorder)
    opened.isVisible = true
    opened.repaint()
  }

  private fun servedBorderIn(visibleArea: Rectangle): Area? {
    val servedArea = servedArea ?: return null
    if (!servedArea.intersects(visibleArea)) {
      return null
    }
    return servedArea.borderRing()
  }

  /**
   * Fills the borders. Every other pixel of the window stays transparent, so only a border reaches the screen.
   */
  private class CacheMarks : JComponent() {
    private var zoneBorders: Area = Area()
    private var servedBorder: Area? = null

    init {
      isOpaque = false
    }

    fun showBorders(zoneBorders: Area, servedBorder: Area?) {
      this.zoneBorders = zoneBorders
      this.servedBorder = servedBorder
    }

    override fun paintComponent(graphics: Graphics) {
      val painter = graphics as Graphics2D
      painter.color = CACHED_ZONE_COLOR
      painter.fill(zoneBorders)
      val servedBorder = this.servedBorder ?: return
      painter.color = SERVED_AREA_COLOR
      painter.fill(servedBorder)
    }
  }

  companion object {
    /**
     * Creates the window and ties it to the lifetime of [editor], or returns `null` when the platform cannot make a
     * window transparent per pixel. Such a window would cover the editor with an opaque rectangle instead of showing
     * only the borders.
     */
    fun createIfSupported(editor: EditorImpl, entries: CacheEntryList): EditorAnimationCacheDebugWindow? {
      val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
      if (!device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
        return null
      }
      val window = EditorAnimationCacheDebugWindow(editor, entries)
      EditorUtil.disposeWithEditor(editor, window)
      return window
    }

    private const val WINDOW_NAME = "Editor animation cache debug window"

    private val CACHED_ZONE_COLOR: Color = JBColor.GREEN

    private val SERVED_AREA_COLOR: Color = JBColor.RED
  }
}
