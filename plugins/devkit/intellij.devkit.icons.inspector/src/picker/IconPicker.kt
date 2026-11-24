// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.picker

import com.intellij.devkit.icons.inspector.DevkitIconsInspectorBundle
import com.intellij.devkit.icons.inspector.model.PickedIcon
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.AWTEvent
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Window
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.util.Collections
import java.util.WeakHashMap
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JRootPane
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import javax.swing.Timer

private val LOG = logger<IconPicker>()

private val HIGHLIGHT_COLOR = JBColor(Color(0x3574F0), Color(0x548AF7))

/** How the picker receives the mouse events of a window. */
internal enum class PickerHook {
  /** Mouse preprocessors on the window's [IdeGlassPane] (IDE frames, `DialogWrapper` dialogs, resizable popups, …). */
  GLASS_PANE,
  /**
   * An [IdeEventQueue] dispatcher filtering the window's mouse events: windows without an [IdeGlassPane] (heavyweight
   * non-resizable popups, tooltips, plain `JDialog`/`JWindow`/Compose windows, non-Swing windows).
   */
  EVENT_QUEUE,
}

/** Which hook the picker uses for a window with [rootPane] (`null` for windows that are not [RootPaneContainer]s). */
internal fun pickerHookFor(rootPane: JRootPane?): PickerHook =
  if (rootPane?.glassPane is IdeGlassPane) PickerHook.GLASS_PANE else PickerHook.EVENT_QUEUE

/**
 * Where the highlight overlay goes in a window with [rootPane]: its [IdeGlassPane] when there is one (as before), else the
 * layered pane's [JLayeredPane.DRAG_LAYER] (a plain glass pane is invisible, so children added to it would not paint).
 */
internal fun overlayHostFor(rootPane: JRootPane): JComponent =
  if (pickerHookFor(rootPane) == PickerHook.GLASS_PANE) rootPane.glassPane as JComponent else rootPane.layeredPane

/**
 * On-screen picker over all IDE windows, mirroring `UiThemeColorPicker.setupWindowListeners`: per-window glass-pane mouse
 * preprocessors, a translucent highlight with a label while hovering, click to pick (the click is consumed), Esc or
 * right-click to cancel. Everything is registered against this [Disposable]; it disposes itself when picking ends.
 *
 * Windows without an [IdeGlassPane] (see [PickerHook.EVENT_QUEUE]) get the same hover/click handling through an
 * [IdeEventQueue] dispatcher, with the highlight drawn on their layered pane. Windows opened while picking (e.g. a dialog
 * opened by a shortcut) are hooked when they open. Modal dialogs run their own event loop, but it still dispatches through
 * [IdeEventQueue], so both hooks keep working inside them.
 *
 * EDT only.
 */
internal class IconPicker(
  private val onPicked: (PickedIcon) -> Unit,
  private val onCancelled: () -> Unit,
) : Disposable {
  private val hookedGlassPanes: MutableSet<IdeGlassPane> = Collections.newSetFromMap(WeakHashMap())
  private var overlay: HighlightOverlay? = null
  private var finished = false
  private var disposed = false
  private var disposeTimer: Timer? = null

  /** Hit-testing of icons-API icons inside Compose hosts; re-runs the hover hit-test when new draws arrive. */
  private val compose = ComposeHitTracker {
    val event = lastHover
    if (!finished && !disposed && event != null) hover(event)
  }
  private var lastHover: MouseEvent? = null

  fun start() {
    ApplicationManager.getApplication().assertIsDispatchThread()
    compose.open()
    for (window in Window.getWindows()) {
      setupWindowListeners(window)
    }
    // windows opened while picking (COMPONENT_EVENT_MASK is needed for heavyweight popups)
    val mask = AWTEvent.WINDOW_EVENT_MASK or AWTEvent.WINDOW_STATE_EVENT_MASK or AWTEvent.COMPONENT_EVENT_MASK
    StartupUiUtil.addAwtListener(mask, this) { event ->
      val source = event.source
      if (source is Window) setupWindowListeners(source)
    }
    IdeEventQueue.getInstance().addDispatcher(IdeEventQueue.NonLockedEventDispatcher { event ->
      when {
        event is KeyEvent && event.keyCode == KeyEvent.VK_ESCAPE && !finished -> {
          if (event.id == KeyEvent.KEY_PRESSED) {
            cancel()
          }
          true
        }
        event is MouseEvent -> dispatchWithoutGlassPane(event)
        else -> false
      }
    }, this)
  }

  /** Cancels picking (no-op once a pick happened). */
  fun cancel() {
    if (finished) {
      Disposer.dispose(this)
      return
    }
    finish(null)
    Disposer.dispose(this)
  }

  private fun setupWindowListeners(window: Window) {
    if (disposed) return
    val rootPane = (window as? RootPaneContainer)?.rootPane ?: return
    if (pickerHookFor(rootPane) != PickerHook.GLASS_PANE) return // handled by dispatchWithoutGlassPane
    val glassPane = rootPane.glassPane as IdeGlassPane
    if (!hookedGlassPanes.add(glassPane)) return

    val motionListener = object : MouseMotionAdapter() {
      override fun mouseMoved(e: MouseEvent) {
        if (!finished) hover(e)
      }

      override fun mouseDragged(e: MouseEvent) {
        if (!finished) e.consume()
      }
    }
    val mouseListener = object : MouseAdapter() {
      override fun mousePressed(e: MouseEvent) {
        e.consume()
        pressed(e)
      }

      override fun mouseReleased(e: MouseEvent) {
        e.consume()
        released()
      }

      override fun mouseClicked(e: MouseEvent) {
        e.consume()
        clicked()
      }
    }
    glassPane.addMousePreprocessor(mouseListener, this)
    glassPane.addMouseMotionPreprocessor(motionListener, this)
  }

  /**
   * [PickerHook.EVENT_QUEUE] path: the mouse events of windows without an [IdeGlassPane]. Returns `true` to consume.
   * Windows whose glass pane was not hooked yet (missed by the AWT listener) are hooked for their next events.
   */
  private fun dispatchWithoutGlassPane(e: MouseEvent): Boolean {
    if (disposed) return false
    val component = e.component ?: return false
    val window = component as? Window ?: SwingUtilities.getWindowAncestor(component) ?: return false
    val rootPane = (window as? RootPaneContainer)?.rootPane
    if (pickerHookFor(rootPane) == PickerHook.GLASS_PANE) {
      setupWindowListeners(window)
      return false
    }
    return when (e.id) {
      MouseEvent.MOUSE_MOVED -> {
        if (!finished) hover(e)
        false
      }
      MouseEvent.MOUSE_DRAGGED -> !finished
      MouseEvent.MOUSE_PRESSED -> {
        pressed(e)
        true
      }
      MouseEvent.MOUSE_RELEASED -> {
        released()
        true
      }
      MouseEvent.MOUSE_CLICKED -> {
        clicked()
        true
      }
      else -> false
    }
  }

  private fun pressed(e: MouseEvent) {
    if (finished) return
    if (SwingUtilities.isRightMouseButton(e)) {
      finish(null)
      return
    }
    if (!SwingUtilities.isLeftMouseButton(e)) return
    val picked = pickAt(e)
    if (picked != null) finish(picked)
  }

  /** Keeps swallowing until the click that follows the release, then cleans up. */
  private fun released() {
    if (finished) scheduleDispose(300)
  }

  private fun clicked() {
    if (finished) Disposer.dispose(this)
  }

  private fun pickAt(e: MouseEvent): PickedIcon? {
    val source = e.component ?: return null
    val deepest = UIUtil.getDeepestComponentAt(source, e.x, e.y) ?: return null
    if (deepest is HighlightOverlay) return null
    val point = SwingUtilities.convertPoint(source, e.point, deepest)
    return compose.find(deepest, point)
  }

  private fun hover(e: MouseEvent) {
    lastHover = e
    val source = e.component ?: return hideOverlay()
    val deepest = UIUtil.getDeepestComponentAt(source, e.x, e.y) ?: return hideOverlay()
    if (deepest is HighlightOverlay) return
    val picked = try {
      compose.find(deepest, SwingUtilities.convertPoint(source, e.point, deepest))
    }
    catch (t: Throwable) {
      LOG.debug(t)
      null
    }
    if (picked != null) {
      showOverlay(picked.component, picked.highlightBounds, picked.label, HIGHLIGHT_COLOR)
    }
    else {
      showOverlay(deepest, null, DevkitIconsInspectorBundle.message("model.picker.no.icon", deepest.javaClass.simpleName), JBColor.GRAY)
    }
  }

  private fun showOverlay(component: Component, bounds: Rectangle?, label: String, color: Color) {
    val rootPane = SwingUtilities.getRootPane(component) ?: return hideOverlay()
    val host = overlayHostFor(rootPane)
    val rect = if (bounds != null) SwingUtilities.convertRectangle(component, bounds, host)
    else Rectangle(SwingUtilities.convertPoint(component, Point(0, 0), host), component.size)
    var current = overlay
    if (current != null && current.parent !== host) {
      hideOverlay()
      current = null
    }
    if (current == null) {
      current = HighlightOverlay()
      overlay = current
      if (host is JLayeredPane) host.add(current, JLayeredPane.DRAG_LAYER as Any) else host.add(current)
    }
    current.update(rect, label, color, host.size)
    host.revalidate()
    host.repaint()
  }

  private fun hideOverlay() {
    val current = overlay ?: return
    overlay = null
    val parent = current.parent ?: return
    parent.remove(current)
    parent.repaint()
  }

  private fun finish(picked: PickedIcon?) {
    if (finished) return
    finished = true
    hideOverlay()
    // safety net in case the release/click never arrives
    scheduleDispose(2000)
    try {
      if (picked != null) onPicked(picked) else onCancelled()
    }
    catch (t: Throwable) {
      LOG.error(t)
    }
  }

  private fun scheduleDispose(delayMs: Int) {
    if (disposed) return
    disposeTimer?.stop()
    disposeTimer = Timer(delayMs) { Disposer.dispose(this) }.apply {
      isRepeats = false
      start()
    }
  }

  override fun dispose() {
    if (disposed) return
    disposed = true
    disposeTimer?.stop()
    disposeTimer = null
    compose.close()
    lastHover = null
    hideOverlay()
    hookedGlassPanes.clear()
    if (!finished) {
      finished = true
      try {
        onCancelled()
      }
      catch (t: Throwable) {
        LOG.error(t)
      }
    }
  }
}

/** Translucent highlight with a small label, added to a glass pane; never receives mouse events. */
private class HighlightOverlay : JComponent() {
  private var highlight = Rectangle()
  private var labelBounds = Rectangle()
  private var label = ""
  private var color: Color = HIGHLIGHT_COLOR

  init {
    isOpaque = false
  }

  fun update(rect: Rectangle, label: String, color: Color, paneSize: java.awt.Dimension) {
    this.label = label
    this.color = color
    val fm = getFontMetrics(JBUI.Fonts.smallFont())
    val padding = JBUI.scale(3)
    val labelWidth = fm.stringWidth(label) + 2 * padding
    val labelHeight = fm.height + 2 * padding
    val highlightRect = Rectangle(rect.x, rect.y, maxOf(rect.width, 1), maxOf(rect.height, 1))
    var labelY = highlightRect.y + highlightRect.height + JBUI.scale(2)
    if (labelY + labelHeight > paneSize.height) labelY = highlightRect.y - labelHeight - JBUI.scale(2)
    var labelX = highlightRect.x
    if (labelX + labelWidth > paneSize.width) labelX = paneSize.width - labelWidth
    val labelRect = Rectangle(maxOf(labelX, 0), maxOf(labelY, 0), labelWidth, labelHeight)
    val union = highlightRect.union(labelRect)
    bounds = union
    highlight = Rectangle(highlightRect.x - union.x, highlightRect.y - union.y, highlightRect.width, highlightRect.height)
    labelBounds = Rectangle(labelRect.x - union.x, labelRect.y - union.y, labelRect.width, labelRect.height)
  }

  override fun contains(x: Int, y: Int): Boolean = false

  override fun paintComponent(g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
      g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.2f)
      g2.color = color
      g2.fillRect(highlight.x, highlight.y, highlight.width, highlight.height)
      g2.composite = AlphaComposite.SrcOver
      g2.color = color.darker()
      g2.drawRect(highlight.x, highlight.y, highlight.width - 1, highlight.height - 1)

      g2.color = JBColor.namedColor("ToolTip.background", JBColor(Color(0xF7F8FA), Color(0x2B2D30)))
      g2.fillRect(labelBounds.x, labelBounds.y, labelBounds.width, labelBounds.height)
      g2.color = color
      g2.drawRect(labelBounds.x, labelBounds.y, labelBounds.width - 1, labelBounds.height - 1)
      g2.font = JBUI.Fonts.smallFont()
      g2.color = JBColor.namedColor("ToolTip.foreground", JBColor.foreground())
      val fm = g2.fontMetrics
      val padding = JBUI.scale(3)
      g2.drawString(label, labelBounds.x + padding, labelBounds.y + padding + fm.ascent)
    }
    finally {
      g2.dispose()
    }
  }
}
