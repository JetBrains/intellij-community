// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.context.screenCapture

import com.intellij.agent.workbench.prompt.ui.AgentPromptBundle
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.popup.StackingPopupDispatcher
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ComponentUtil
import com.intellij.ui.JBColor
import java.awt.AWTEvent
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Robot
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JWindow
import javax.swing.SwingUtilities

private const val MIN_SELECTION_SIDE = 12
private val OVERLAY_DIM_COLOR = JBColor(Color(0, 0, 0, 140), Color(0, 0, 0, 140))
private val SELECTION_BORDER_COLOR = JBColor(Color(0x5B, 0x9B, 0xFF), Color(0x5B, 0x9B, 0xFF))
private val SELECTION_FILL_COLOR = JBColor(Color(0x5B, 0x9B, 0xFF, 40), Color(0x5B, 0x9B, 0xFF, 40))
private val HINT_BACKGROUND_COLOR = JBColor(Color(0, 0, 0, 190), Color(0, 0, 0, 190))
private val HINT_FOREGROUND_COLOR = JBColor(Color.WHITE, Color.WHITE)

private val LOG = logger<ScreenAreaPickerSession>()

internal data class ScreenSnapshot(
  val bounds: Rectangle,
  val image: BufferedImage,
)

internal fun normalizeSelectionBounds(start: Point, end: Point): Rectangle {
  val x = minOf(start.x, end.x)
  val y = minOf(start.y, end.y)
  return Rectangle(x, y, kotlin.math.abs(end.x - start.x), kotlin.math.abs(end.y - start.y))
}

internal fun captureSelectionFromSnapshots(
  snapshots: List<ScreenSnapshot>,
  selection: Rectangle,
): BufferedImage? {
  if (selection.width <= 0 || selection.height <= 0) return null

  @Suppress("UndesirableClassUsage")
  val result = BufferedImage(selection.width, selection.height, BufferedImage.TYPE_INT_ARGB)
  val graphics = result.createGraphics()
  var hasPixels = false
  try {
    snapshots.forEach { snapshot ->
      val intersection = snapshot.bounds.intersection(selection)
      if (intersection.width <= 0 || intersection.height <= 0) return@forEach

      val scaleX = snapshot.image.width.toDouble() / snapshot.bounds.width
      val scaleY = snapshot.image.height.toDouble() / snapshot.bounds.height
      val sourceX = ((intersection.x - snapshot.bounds.x) * scaleX).toInt()
      val sourceY = ((intersection.y - snapshot.bounds.y) * scaleY).toInt()
      val sourceW = (intersection.width * scaleX).toInt()
      val sourceH = (intersection.height * scaleY).toInt()
      val destX = intersection.x - selection.x
      val destY = intersection.y - selection.y
      graphics.drawImage(
        snapshot.image,
        destX,
        destY,
        destX + intersection.width,
        destY + intersection.height,
        sourceX,
        sourceY,
        sourceX + sourceW,
        sourceY + sourceH,
        null,
      )
      hasPixels = true
    }
  }
  finally {
    graphics.dispose()
  }

  return result.takeIf { hasPixels }
}

private fun isMeaningfulSelection(selection: Rectangle): Boolean {
  return selection.width >= MIN_SELECTION_SIDE && selection.height >= MIN_SELECTION_SIDE
}

/**
 * Manages an interactive screen area pick session: shows full-screen overlays
 * with a dimmed screenshot and lets the user drag-select an area to capture.
 *
 * Uses [IdeEventQueue.addPostEventListener] to intercept events at **posting** time,
 * before they enter the event queue. This is the earliest interception point and prevents
 * events from ever reaching popup cancel handlers (which run during dispatch).
 * Without this, the [com.intellij.ui.popup.StackingPopupDispatcherImpl] would see
 * mouse/key events from the overlay and cancel the prompt's [com.intellij.openapi.ui.popup.JBPopup].
 *
 * The hook must not call `kotlinx.coroutines.launch` or `LaterInvocator.invokeLater`
 * because it runs inside [sun.awt.PostEventQueue.flush] which holds an AWT-internal lock.
 * All deferred work is scheduled via [SwingUtilities.invokeLater] which posts directly
 * to the AWT [java.awt.EventQueue] and bypasses IntelliJ's `NonBlockingFlushQueue`.
 */
internal class ScreenAreaPickerSession(
  anchorComponent: Component,
  private val onPicked: (BufferedImage) -> Unit,
  private val onCanceled: () -> Unit,
  private val onError: (String) -> Unit,
) : Disposable {
  private val promptWindow: Window? = ComponentUtil.getWindow(anchorComponent)
  private var promptWindowWasVisible: Boolean = false
  private val active = AtomicBoolean(true)
  private val overlayWindows = ArrayList<ScreenCaptureOverlayWindow>()

  private var selectionStart: Point? = null
  private var selectionEnd: Point? = null
  private var isSelecting: Boolean = false

  fun start() {
    SwingUtilities.invokeLater {
      if (!active.get()) return@invokeLater
      IdeEventQueue.getInstance().addPostEventListener(postEventHook, this)
      hidePromptWindow()
      // Wait for the native compositor to finish hiding the window before capturing
      // the screen. Without this, the prompt popup may still appear in the screenshot.
      Toolkit.getDefaultToolkit().sync()
      Robot().delay(150)
      showOverlays()
    }
  }

  private val postEventHook: (AWTEvent) -> Boolean = { event ->
    if (!active.get()) {
      false
    }
    else if (event is KeyEvent && event.id == KeyEvent.KEY_PRESSED && event.keyCode == KeyEvent.VK_ESCAPE) {
      SwingUtilities.invokeLater { cancel() }
      true
    }
    else if (event is MouseEvent) {
      handleMouseEvent(event)
    }
    else {
      false
    }
  }

  private fun handleMouseEvent(event: MouseEvent): Boolean {
    when (event.id) {
      MouseEvent.MOUSE_PRESSED -> {
        if (SwingUtilities.isLeftMouseButton(event)) {
          val screenPoint = Point(event.xOnScreen, event.yOnScreen)
          SwingUtilities.invokeLater { beginSelection(screenPoint) }
        }
      }
      MouseEvent.MOUSE_DRAGGED -> {
        if (SwingUtilities.isLeftMouseButton(event)) {
          val screenPoint = Point(event.xOnScreen, event.yOnScreen)
          SwingUtilities.invokeLater { updateSelection(screenPoint) }
        }
      }
      MouseEvent.MOUSE_RELEASED -> {
        if (SwingUtilities.isLeftMouseButton(event)) {
          val screenPoint = Point(event.xOnScreen, event.yOnScreen)
          SwingUtilities.invokeLater { finishSelection(screenPoint) }
        }
      }
    }
    // Consume all mouse events so they never reach the popup stacking dispatcher
    return true
  }

  private fun showOverlays() {
    val snapshots = runCatching { captureScreenSnapshots() }
      .onFailure { error -> LOG.warn(error) }
      .getOrDefault(emptyList())

    if (snapshots.isEmpty()) {
      finishWithError(AgentPromptBundle.message("manual.context.screen.error.unavailable"))
      return
    }

    val pointerLocation = MouseInfo.getPointerInfo()?.location
    val focusedOverlayBounds = snapshots.firstOrNull { snapshot -> pointerLocation != null && snapshot.bounds.contains(pointerLocation) }?.bounds

    try {
      snapshots.forEach { snapshot ->
        val overlay = ScreenCaptureOverlayWindow(snapshot, this)
        overlayWindows += overlay
        overlay.showWindow(requestFocus = snapshot.bounds == focusedOverlayBounds)
      }
    }
    catch (e: Exception) {
      LOG.warn(e)
      finishWithError(AgentPromptBundle.message("manual.context.screen.error.unavailable"))
    }
  }

  internal fun selectionBounds(): Rectangle? {
    val start = selectionStart ?: return null
    val end = selectionEnd ?: return null
    return normalizeSelectionBounds(start, end)
  }

  internal fun beginSelection(screenPoint: Point) {
    if (!active.get()) return
    selectionStart = screenPoint
    selectionEnd = screenPoint
    isSelecting = true
    repaintOverlays()
  }

  internal fun updateSelection(screenPoint: Point) {
    if (!active.get() || !isSelecting) return
    selectionEnd = screenPoint
    repaintOverlays()
  }

  internal fun finishSelection(screenPoint: Point) {
    if (!active.get() || !isSelecting) return

    selectionEnd = screenPoint
    isSelecting = false
    val selection = selectionBounds()
    if (selection == null || !isMeaningfulSelection(selection)) {
      selectionStart = null
      selectionEnd = null
      repaintOverlays()
      return
    }

    val screenshot = captureSelectionFromSnapshots(overlayWindows.map(ScreenCaptureOverlayWindow::snapshot), selection)
    stop()
    if (screenshot != null) {
      onPicked(screenshot)
    }
    else {
      onError(AgentPromptBundle.message("manual.context.screen.error.capture"))
    }
  }

  internal fun cancel() {
    if (!active.get()) return
    stop()
    onCanceled()
  }

  internal fun repaintOverlays() {
    overlayWindows.forEach(ScreenCaptureOverlayWindow::repaintOverlay)
  }

  private fun stop() {
    if (!active.compareAndSet(true, false)) return
    overlayWindows.forEach(ScreenCaptureOverlayWindow::disposeWindow)
    overlayWindows.clear()
    restorePromptWindow()
    // While the prompt window was hidden, IdePopupManager.isPopupActive() removed the
    // StackingPopupDispatcherImpl from its dispatch stack (content was not showing).
    // Re-push it so that Esc handling works again for the prompt's JBPopup.
    IdeEventQueue.getInstance().popupManager.push(StackingPopupDispatcher.getInstance())
    Disposer.dispose(this)
  }

  private fun finishWithError(message: String) {
    stop()
    onError(message)
  }

  private fun hidePromptWindow() {
    promptWindowWasVisible = promptWindow?.isVisible == true
    if (promptWindowWasVisible) {
      promptWindow?.isVisible = false
    }
  }

  private fun restorePromptWindow() {
    if (!promptWindowWasVisible) return
    promptWindow?.isVisible = true
    promptWindow?.toFront()
    promptWindow?.requestFocus()
  }

  override fun dispose() {}
}

private fun captureScreenSnapshots(): List<ScreenSnapshot> {
  return GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.mapNotNull { device ->
    val bounds = device.defaultConfiguration.bounds
    if (bounds.width <= 0 || bounds.height <= 0) return@mapNotNull null

    val image = runCatching { Robot(device).createScreenCapture(bounds) }
      .onFailure { error -> LOG.warn(error) }
      .getOrNull()
      ?: return@mapNotNull null

    ScreenSnapshot(bounds = Rectangle(bounds), image = image)
  }
}

private class ScreenCaptureOverlayWindow(
  val snapshot: ScreenSnapshot,
  private val session: ScreenAreaPickerSession,
) : JWindow() {
  private val overlayComponent = object : JComponent() {
    override fun paintComponent(graphics: Graphics) {
      val g = graphics.create() as? Graphics2D ?: return
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.drawImage(snapshot.image, 0, 0, width, height, null)
        g.color = OVERLAY_DIM_COLOR
        g.fillRect(0, 0, width, height)

        val selection = session.selectionBounds()
        if (selection != null) {
          paintSelection(g, selection)
        }
        else {
          paintHint(g)
        }
      }
      finally {
        g.dispose()
      }
    }

    private fun paintSelection(g: Graphics2D, selection: Rectangle) {
      val localSelection = Rectangle(selection)
      localSelection.translate(-snapshot.bounds.x, -snapshot.bounds.y)
      val visibleSelection = localSelection.intersection(Rectangle(0, 0, width, height))
      if (visibleSelection.width > 0 && visibleSelection.height > 0) {
        val sx = snapshot.image.width.toDouble() / snapshot.bounds.width
        val sy = snapshot.image.height.toDouble() / snapshot.bounds.height
        g.drawImage(
          snapshot.image,
          visibleSelection.x,
          visibleSelection.y,
          visibleSelection.x + visibleSelection.width,
          visibleSelection.y + visibleSelection.height,
          (visibleSelection.x * sx).toInt(),
          (visibleSelection.y * sy).toInt(),
          ((visibleSelection.x + visibleSelection.width) * sx).toInt(),
          ((visibleSelection.y + visibleSelection.height) * sy).toInt(),
          null,
        )
        g.color = SELECTION_FILL_COLOR
        g.fillRect(visibleSelection.x, visibleSelection.y, visibleSelection.width, visibleSelection.height)
      }

      g.color = SELECTION_BORDER_COLOR
      g.stroke = BasicStroke(2f)
      g.drawRect(localSelection.x, localSelection.y, localSelection.width, localSelection.height)
    }

    private fun paintHint(g: Graphics2D) {
      val hint = AgentPromptBundle.message("manual.context.screen.picker.hint")
      val metrics: FontMetrics = g.fontMetrics
      val padding = 12
      val textWidth = metrics.stringWidth(hint)
      val textHeight = metrics.height
      val boxWidth = textWidth + padding * 2
      val boxHeight = textHeight + padding * 2
      val x = (width - boxWidth) / 2
      val y = height / 8

      g.color = HINT_BACKGROUND_COLOR
      g.fillRoundRect(x, y, boxWidth, boxHeight, 16, 16)
      g.color = HINT_FOREGROUND_COLOR
      g.drawString(hint, x + padding, y + padding + metrics.ascent)
    }
  }

  init {
    isAlwaysOnTop = true
    focusableWindowState = true
    background = JBColor.BLACK
    rootPane.putClientProperty("Window.shadow", false)
    bounds = snapshot.bounds
    minimumSize = Dimension(snapshot.bounds.width, snapshot.bounds.height)
    contentPane = overlayComponent
    // Mouse and key events are intercepted by the session's post-event hook
    // (registered via IdeEventQueue.addPostEventListener) to prevent the
    // StackingPopupDispatcherImpl from cancelling the prompt popup.
  }

  fun showWindow(requestFocus: Boolean) {
    isVisible = true
    toFront()
    if (requestFocus) {
      requestFocus()
      overlayComponent.requestFocusInWindow()
    }
  }

  fun repaintOverlay() {
    overlayComponent.repaint()
  }

  fun disposeWindow() {
    isVisible = false
    dispose()
  }
}
