// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac

import com.intellij.openapi.util.Key
import com.intellij.ui.ClientProperty
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.FullScreenSupport
import com.intellij.ui.JBColor
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.paint.use
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBSwingUtilities
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import com.jetbrains.WindowDecorations.CustomTitleBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JRootPane
import javax.swing.RootPaneContainer
import javax.swing.SwingUtilities
import javax.swing.border.AbstractBorder
import kotlin.math.ceil

/**
 * The content that a window shows in the title bar of macOS, in one row with the window buttons.
 */
@ApiStatus.Internal
interface MacTitleBarContent {
  /**
   * The height of the title bar row in the user space of the window, measured from the top of the layered pane.
   */
  val height: Int

  /**
   * Returns true if a press on [component] moves the window and a double click zooms it.
   * [component] is the deepest component under the mouse in the title bar row.
   */
  fun isTitleBarArea(component: Component): Boolean

  /**
   * True when a native view of the content moves the window itself, as a web view of AppKit does.
   * The runtime then puts no title bar view over the content, so each press in the row reaches the native view.
   * The window does not call [isTitleBarArea], and the native side places the window buttons.
   */
  val movesWindowItself: Boolean
    get() = false
}

/**
 * The geometry of a merged title bar row.
 *
 * @property height the height of the row in the user space of the window.
 * @property leadingInset the width at the leading side of the row that the window buttons take, in the user space of the window.
 * @property isFullScreen true when the window is in full screen.
 */
@ApiStatus.Internal
data class MacTitleBarGeometry(val height: Int, val leadingInset: Int, val isFullScreen: Boolean)

/**
 * The title bar of a [JFrame] on macOS that is not a project frame.
 *
 * By default, the frame shows the system title bar row with the window title above its content.
 * [merge] puts the content into the title bar of macOS instead, in one row with the window buttons.
 * For a Swing content, a JBR `CustomTitleBar` moves the window on a press in the title bar area of the content.
 * For a content that [MacTitleBarContent.movesWindowItself], the window keeps only the transparent title bar of the system.
 * The title of the window stays set for the Window menu and for accessibility.
 */
@ApiStatus.Internal
class MacWindowTitleBar internal constructor(
  private val frame: JFrame,
  private val rootPane: JRootPane,
  private val fullScreenSupport: FullScreenSupport?,
) {
  private val decorations: WindowDecorations? = if (ExperimentalUI.isNewUI()) JBR.getWindowDecorations() else null
  private val systemBorder = MacSystemTitleBarBorder(frame) { fullScreenSupport?.isFullScreen() == true }
  private val mergedState = MutableStateFlow<MacTitleBarGeometry?>(null)
  private var customTitleBar: CustomTitleBar? = null
  private val hitTestListener = AWTEventListener { event -> if (event is MouseEvent) hitTest(event) }

  /**
   * The geometry of the merged title bar row, or null when the frame shows the system title bar row.
   * For a content that [MacTitleBarContent.movesWindowItself], the native side places the window buttons,
   * so the leading inset is only the gap of the project frame.
   */
  val merged: StateFlow<MacTitleBarGeometry?> = mergedState.asStateFlow()

  /**
   * The content in the title bar row, or null when the frame shows the system title bar row.
   */
  var content: MacTitleBarContent? = null
    private set

  /**
   * True when the runtime can merge a content into the title bar: the New UI on a JetBrains Runtime with window decorations.
   */
  val canMerge: Boolean
    get() = decorations != null

  internal fun install(onDispose: (Runnable) -> Unit) {
    rootPane.border = systemBorder
    ClientProperty.put(rootPane, KEY, this)

    // macOS doesn't zoom a full-content window on a title bar double-click; the project frame does it in MacMainFrameDecorator
    val doubleClickListener = object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e) && e.y < rootPane.insets.top) {
          MacTitleBarDoubleClick.perform(frame)
        }
      }
    }
    rootPane.addMouseListener(doubleClickListener)

    // the runtime can report the left inset after it applies the title bar, so each of these events reads it again
    val windowListener = object : WindowAdapter() {
      override fun windowOpened(e: WindowEvent) = update()

      override fun windowActivated(e: WindowEvent) = update()
    }
    frame.addWindowListener(windowListener)
    val resizeListener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) = update()
    }
    rootPane.addComponentListener(resizeListener)
    val uiListener = PropertyChangeListener { update() }
    rootPane.addPropertyChangeListener("UI", uiListener)
    val macFullScreenSupport = fullScreenSupport as? MacFullScreenSupport
    macFullScreenSupport?.setChangeListener { update() }

    onDispose {
      unmerge()
      macFullScreenSupport?.setChangeListener(null)
      rootPane.removeMouseListener(doubleClickListener)
      rootPane.removeComponentListener(resizeListener)
      rootPane.removePropertyChangeListener("UI", uiListener)
      frame.removeWindowListener(windowListener)
      ClientProperty.put(rootPane, KEY, null)
    }
  }

  /**
   * Puts [content] into the title bar row of macOS, or returns the system title bar row for null.
   * Call it again with the same content when its height changes.
   * The frame keeps the system title bar row when [canMerge] is false or the height of the content is not positive.
   */
  @RequiresEdt
  fun merge(content: MacTitleBarContent?) {
    val decorations = decorations
    val height = content?.height ?: 0
    if (content == null || decorations == null || height <= 0) {
      unmerge()
      return
    }

    if (this.content == null) {
      rootPane.putClientProperty(WINDOW_TITLE_VISIBLE, false)
      rootPane.border = JBUI.Borders.empty()
      rootPane.revalidate()
      rootPane.repaint()
    }
    this.content = content
    if (content.movesWindowItself) {
      removeCustomTitleBar()
    }
    else {
      val bar = customTitleBar
      if (bar == null) {
        val newBar = decorations.createCustomTitleBar()
        newBar.height = height.toFloat()
        customTitleBar = newBar
        decorations.setCustomTitleBar(frame, newBar)
        Toolkit.getDefaultToolkit().addAWTEventListener(hitTestListener, AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK)
        SwingUtilities.invokeLater(::update)
      }
      else {
        bar.height = height.toFloat()
      }
    }
    update()
  }

  private fun removeCustomTitleBar() {
    customTitleBar ?: return
    customTitleBar = null
    Toolkit.getDefaultToolkit().removeAWTEventListener(hitTestListener)
    decorations?.setCustomTitleBar(frame, null)
  }

  private fun unmerge() {
    content ?: return
    content = null
    removeCustomTitleBar()
    rootPane.putClientProperty(WINDOW_TITLE_VISIBLE, true)
    rootPane.border = systemBorder
    rootPane.revalidate()
    rootPane.repaint()
    mergedState.value = null
  }

  private fun update() {
    val content = content ?: return
    val bar = customTitleBar
    val isFullScreen = fullScreenSupport?.isFullScreen() == true
    mergedState.value = macTitleBarGeometry(height = bar?.height ?: content.height.toFloat(),
                                            leftInset = bar?.leftInset ?: 0f,
                                            isFullScreen = isFullScreen)
  }

  private fun hitTest(event: MouseEvent) {
    val bar = customTitleBar ?: return
    val content = content ?: return
    if (event.id == MouseEvent.MOUSE_EXITED || event.id == MouseEvent.MOUSE_WHEEL) {
      return
    }

    val source = event.component ?: return
    if (SwingUtilities.getRoot(source) !== frame) {
      return
    }

    val layeredPane = rootPane.layeredPane
    val point = SwingUtilities.convertPoint(source, event.point, layeredPane)
    if (point.y < 0 || point.y >= bar.height) {
      return
    }

    val deepest = SwingUtilities.getDeepestComponentAt(layeredPane, point.x, point.y)
    bar.forceHitTest(event.isPopupTrigger || (deepest != null && !content.isTitleBarArea(deepest)))
  }

  companion object {
    private val KEY = Key.create<MacWindowTitleBar>("MacWindowTitleBar")

    /**
     * Returns the title bar of [window], or null if the window has none.
     */
    @JvmStatic
    fun of(window: Window): MacWindowTitleBar? {
      val rootPane = (window as? RootPaneContainer)?.rootPane ?: return null
      return ClientProperty.get(rootPane, KEY)
    }
  }
}

/**
 * Computes the geometry of a merged title bar row.
 * The window buttons take [leftInset] that the runtime reports. Before the runtime reports it, and in full screen,
 * they take [macWindowButtonsGap].
 */
internal fun macTitleBarGeometry(
  height: Float,
  leftInset: Float,
  isFullScreen: Boolean,
  newFullScreenControls: Boolean = MacFullScreenControlsManager.enabled(),
): MacTitleBarGeometry {
  val leadingInset = if (!isFullScreen && leftInset > 0) {
    ceil(leftInset).toInt()
  }
  else {
    JBUI.scale(macWindowButtonsGap(isFullScreen = isFullScreen, newFullScreenControls = newFullScreenControls))
  }
  return MacTitleBarGeometry(height = ceil(height).toInt(), leadingInset = leadingInset, isFullScreen = isFullScreen)
}

/**
 * The border of a root pane that keeps the space of the transparent system title bar row and paints its background.
 */
internal class MacSystemTitleBarBorder(private val window: Window, private val isFullScreen: () -> Boolean) : AbstractBorder() {
  override fun getBorderInsets(c: Component): Insets {
    if (isFullScreen()) {
      return JBInsets.emptyInsets()
    }
    // native window title bar doesn't scale
    @Suppress("UseDPIAwareInsets")
    return Insets(topInset(c), 0, 0, 0)
  }

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    if (isFullScreen()) {
      return
    }

    val top = topInset(c)
    (g.create() as Graphics2D).let {
      if (c is JComponent) JBSwingUtilities.runGlobalCGTransform(c, it) else it
    }.use { graphics ->
      graphics.color = JBColor.PanelBackground
      graphics.fill(Rectangle(0, 0, c.width, top))
      val pane = (window as? RootPaneContainer)?.rootPane
      if (window is RootPaneContainer && (pane == null || pane.getClientProperty(UIUtil.NO_BORDER_UNDER_WINDOW_TITLE_KEY) == false)) {
        graphics.color = JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()
        LinePainter2D.paint(graphics, 0.0, (top - 1).toDouble(), c.width.toDouble(), (top - 1).toDouble(),
                            LinePainter2D.StrokeType.INSIDE, 1.0)
      }
    }
  }

  private fun topInset(c: Component): Int = (c as? JRootPane)?.let(UIUtil::getTransparentTitleBarHeight) ?: 0
}

private const val WINDOW_TITLE_VISIBLE = "apple.awt.windowTitleVisible"
