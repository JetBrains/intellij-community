// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context.uiPicker

import com.intellij.agent.workbench.prompt.AgentPromptBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.ComponentUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.AWTEvent
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.MouseInfo
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

/**
 * Modeless popup that follows the mouse cursor and shows a hint about how to use the picker.
 */
internal class PickerTooltip(owner: Component, componentName: String) :
  JDialog(ComponentUtil.getWindow(owner), null, ModalityType.MODELESS) {

  companion object {
    fun isInTooltip(component: Component): Boolean {
      var current: Component? = component
      while (current != null) {
        if (current is PickerTooltip) return true
        current = current.parent
      }
      return false
    }
  }

  init {
    type = Type.POPUP
    focusableWindowState = false
    isUndecorated = true
    isAlwaysOnTop = true
    val panel = BorderLayoutPanel()
    val padding = JBUIScale.scale(4)
    panel.border = JBUI.Borders.empty(padding)
    val displayName = componentName.ifBlank { "component" }.let { if (it.length > 30) it.take(30) + "\u2026" else it }
    val label = JBLabel(AgentPromptBundle.message("manual.context.ui.picker.hint", displayName))
    panel.addToCenter(label)
    contentPane = panel
    size = Dimension(label.preferredSize.width + padding * 2, label.preferredSize.height + padding * 2)
  }

  fun followMouse(screenX: Int, screenY: Int) {
    setLocation(screenX + JBUIScale.scale(20), screenY)
    if (!isVisible) isVisible = true
  }
}

/**
 * Transparent overlay that highlights a Swing component with a rounded purple border.
 */
internal class UIContextPickerHighlight(val targetComponent: Component) : JComponent(), Disposable {
  companion object {
    private val RADIUS = JBUIScale.scale(25)
    private val HIGHLIGHT_COLOR: JBColor = JBColor(Color(0x834DF0), Color(0xA571E6))
  }

  val glassPane: JComponent = (SwingUtilities.getRootPane(targetComponent)?.glassPane as? JComponent)
                              ?: throw IllegalStateException("Glass pane not found")

  private val rectangle by lazy {
    Rectangle(0, 0, bounds.width, bounds.height).also { JBInsets.removeFrom(it, JBUI.insets(1)) }
  }

  override fun paintComponent(g: Graphics) {
    val g2d = g.create() as? Graphics2D ?: return
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.color = HIGHLIGHT_COLOR
    g2d.drawRoundRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height, RADIUS, RADIUS)
    g2d.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.1f)
    g2d.fillRoundRect(rectangle.x, rectangle.y, rectangle.width, rectangle.height, RADIUS, RADIUS)
    g2d.dispose()
  }

  override fun dispose() {}
}

/**
 * Manages an interactive UI pick session: intercepts mouse events to let the user
 * hover-highlight and click-select a Swing component, then captures a screenshot of it.
 *
 * Uses [IdeEventQueue.addPostEventListener] to intercept events at **posting** time,
 * before they enter the event queue. This is the earliest interception point and prevents
 * events from ever reaching popup cancel handlers (which run during dispatch).
 *
 * The hook must not call `kotlinx.coroutines.launch` or `LaterInvocator.invokeLater`
 * because it runs inside [sun.awt.PostEventQueue.flush] which holds an AWT-internal lock.
 * All deferred work is scheduled via [SwingUtilities.invokeLater] which posts directly
 * to the AWT [EventQueue] and bypasses IntelliJ's `NonBlockingFlushQueue`.
 */
internal class UIContextPickerSession(
  private val project: Project,
  private val onPicked: (Component, BufferedImage) -> Unit,
  private val onCanceled: () -> Unit,
) : Disposable {

  private val currentHighlight = AtomicReference<UIContextPickerHighlight?>(null)
  private var currentTooltip: PickerTooltip? = null
  private val active = AtomicBoolean(true)

  fun start() {
    IdeEventQueue.getInstance().addPostEventListener(postEventHook, this)
    SwingUtilities.invokeLater { highlightComponentUnderCursor() }
  }

  private fun stop() {
    if (!active.compareAndSet(true, false)) return
    removeCurrentHighlight()
    Disposer.dispose(this)
  }

  private val postEventHook: (AWTEvent) -> Boolean = { event ->
    if (!active.get()) {
      false
    }
    else if (event is MouseEvent) {
      handleMouseEvent(event)
    }
    else if (event is KeyEvent && event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_ESCAPE) {
      SwingUtilities.invokeLater {
        stop()
        onCanceled()
      }
      true
    }
    else {
      false
    }
  }

  private fun handleMouseEvent(e: MouseEvent): Boolean {
    when (e.id) {
      MouseEvent.MOUSE_MOVED -> {
        val screenX = e.xOnScreen
        val screenY = e.yOnScreen
        SwingUtilities.invokeLater { onMouseMoved(screenX, screenY) }
      }
      MouseEvent.MOUSE_CLICKED -> {
        if (e.clickCount == 1 && e.button == MouseEvent.BUTTON1) {
          SwingUtilities.invokeLater { onComponentClicked() }
        }
      }
    }
    // Consume all mouse events so they never enter the queue
    return true
  }

  private fun onComponentClicked() {
    val component = currentHighlight.get()?.targetComponent ?: return

    removeCurrentHighlight()

    val screenshot = captureComponentScreenshot(component)
    stop()

    if (screenshot != null) {
      onPicked(component, screenshot)
    }
    else {
      onCanceled()
    }
  }

  private fun highlightComponentUnderCursor() {
    val component = findComponentUnderCursor() ?: return
    showHighlight(component)
  }

  private fun findComponentUnderCursor(): Component? {
    val ideFrame = WindowManager.getInstance().getIdeFrame(project) ?: return null
    val mousePos = MouseInfo.getPointerInfo()?.location ?: return null
    SwingUtilities.convertPointFromScreen(mousePos, ideFrame.component)

    return UIUtil.getDeepestComponentAt(ideFrame.component, mousePos.x, mousePos.y)
      ?.let { findParentScrollPane(it) }
  }

  private fun onMouseMoved(screenX: Int, screenY: Int) {
    val component = findComponentUnderCursor()
    if (component == null || component is UIContextPickerHighlight || PickerTooltip.isInTooltip(component)) {
      removeCurrentHighlight()
      return
    }
    showHighlight(component)
    currentTooltip?.followMouse(screenX, screenY)
  }

  private fun showHighlight(component: Component) {
    if (!active.get()) return

    val glassPane = (SwingUtilities.getRootPane(component)?.glassPane as? JComponent) ?: return
    val highlight = UIContextPickerHighlight(component)
    val old = currentHighlight.getAndSet(highlight)

    if (old == null || old.targetComponent !== component) {
      disposeTooltip()
      currentTooltip = try { PickerTooltip(component, resolveComponentDisplayName(component)) } catch (_: Exception) { null }
    }

    old?.let { removeHighlightOverlay(it) }

    val componentRect = Rectangle(0, 0, component.width, component.height)
    highlight.bounds = SwingUtilities.convertRectangle(component, componentRect, glassPane)
    glassPane.add(highlight)
    glassPane.revalidate()
    glassPane.repaint()
  }

  private fun removeCurrentHighlight() {
    disposeTooltip()
    currentHighlight.getAndSet(null)?.let { removeHighlightOverlay(it) }
  }

  private fun disposeTooltip() {
    currentTooltip?.dispose()
    currentTooltip = null
  }

  private fun removeHighlightOverlay(highlight: UIContextPickerHighlight) {
    Disposer.dispose(highlight)
    highlight.glassPane.remove(highlight)
    highlight.glassPane.revalidate()
    highlight.glassPane.repaint()
  }

  override fun dispose() {}
}

private fun findParentScrollPane(component: Component): Component {
  var result: Component? = component
  while (result != null) {
    if (result is JScrollPane) return result
    result = result.parent
  }

  return component
}

/**
 * Captures a screenshot of the given [component] by painting it into a [BufferedImage].
 * Handles HiDPI scaling. Must be called on EDT.
 */
internal fun captureComponentScreenshot(component: Component): BufferedImage? {
  if (component.width <= 0 || component.height <= 0) return null

  val scaleFactor = component.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0
  val w = (component.width * scaleFactor).toInt()
  val h = (component.height * scaleFactor).toInt()
  @Suppress("UndesirableClassUsage")
  val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
  val g = image.createGraphics()

  try {
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.scale(scaleFactor, scaleFactor)
    component.paint(g)
  }
  finally {
    g.dispose()
  }

  return image
}
