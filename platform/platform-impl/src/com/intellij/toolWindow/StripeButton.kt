// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.ide.HelpTooltip
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.MnemonicHelper.DISABLE_MNEMONIC_PROCESSING
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.WindowInfo
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.openapi.wm.impl.AnchoredButton
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.PopupHandler
import com.intellij.ui.RelativeFont
import com.intellij.ui.awt.DevicePoint
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.*

/**
 * @author Eugene Belyaev
 */
class StripeButton internal constructor(internal val toolWindow: ToolWindowImpl)
  : AnchoredButton(), UiDataProvider {
  /**
   * This is analog of Swing mnemonic. We cannot use the standard ones
   * because it causes typing of "funny" characters into the editor.
   */
  private var mnemonic = 0
  private var pressedWhenSelected = false
  private var dragPane: JLayeredPane? = null
  private var dragButton: StripeButtonManager? = null
  private var dragButtonImage: JLabel? = null
  private var pressedPoint: Point? = null
  private var lastStripe: AbstractDroppableStripe? = null
  private var dragKeyEventDispatcher: KeyEventDispatcher? = null
  private var dragCancelled = false

  init {
    isFocusable = false
    border = JBUI.Borders.empty(5, 5, 0, 5)
    putClientProperty(DISABLE_MNEMONIC_PROCESSING, true)
    addActionListener {
      val id = toolWindow.id
      val manager = toolWindow.toolWindowManager
      if (pressedWhenSelected) {
        manager.hideToolWindow(id = id, source = ToolWindowEventSource.StripeButton)
      }
      else {
        manager.activated(toolWindow, ToolWindowEventSource.StripeButton)
      }
      pressedWhenSelected = false
    }
    addMouseListener(object : PopupHandler() {
      override fun invokePopup(component: Component, x: Int, y: Int) {
        showPopup(component, x, y)
      }
    })
    isRolloverEnabled = true
    isOpaque = false
    enableEvents(AWTEvent.MOUSE_EVENT_MASK)
    addMouseMotionListener(object : MouseMotionAdapter() {
      override fun mouseDragged(e: MouseEvent) {
        if (!AdvancedSettings.getBoolean("ide.tool.window.header.dnd")) processDrag(e)
      }
    })
    updateHelpTooltip()
  }

  private fun updateHelpTooltip() {
    HelpTooltip.dispose(this)
    val tooltip = HelpTooltip()
    tooltip.setTitle(toolWindow.stripeTitleProvider)
    val activateActionId = ActivateToolWindowAction.Manager.getActionIdForToolWindow(toolWindow.id)
    tooltip.setShortcut(ActionManager.getInstance().getKeyboardShortcut(activateActionId))
    tooltip.installOn(this)
  }

  val windowInfo: WindowInfo
    get() = toolWindow.windowInfo
  val id: String
    get() = toolWindow.id

  override fun uiDataSnapshot(sink: DataSink) {
    sink[PlatformDataKeys.TOOL_WINDOW] = toolWindow
    sink[CommonDataKeys.PROJECT] = toolWindow.toolWindowManager.project
  }

  override fun getMnemonic(): Int = mnemonic

  /**
   * We are using the trick here: the method does all things that super method does
   * except firing of the MNEMONIC_CHANGED_PROPERTY event. After that mnemonic
   * doesn't work via standard Swing rules (processing of Alt keystrokes).
   */
  override fun setMnemonic(mnemonic: Int): Nothing = throw UnsupportedOperationException("use setMnemonic2(int)")

  private fun setMnemonic2(mnemonic: Int) {
    this.mnemonic = mnemonic
    updateHelpTooltip()
    revalidate()
    repaint()
  }

  override fun getMnemonic2(): Int = mnemonic

  override fun getAnchor(): ToolWindowAnchor = toolWindow.anchor

  val isFirst: Boolean
    get() = `is`(true)
  val isLast: Boolean
    get() = `is`(false)

  private fun `is`(first: Boolean): Boolean {
    val parent = parent ?: return false
    var max = if (first) Int.MAX_VALUE else 0
    val anchor = anchor
    var c: Component? = null
    val count = parent.componentCount
    for (i in 0 until count) {
      val component = parent.getComponent(i)
      if (!component.isVisible) continue
      val r = component.bounds
      if (anchor == ToolWindowAnchor.LEFT || anchor == ToolWindowAnchor.RIGHT) {
        if (first && max > r.y || !first && max < r.y) {
          max = r.y
          c = component
        }
      }
      else {
        if (first && max > r.x || !first && max < r.x) {
          max = r.x
          c = component
        }
      }
    }
    return c === this
  }

  private fun processDrag(e: MouseEvent) {
    if (dragCancelled || !MouseDragHelper.checkModifiers(e)) {
      return
    }
    if (!isDraggingNow) {
      if (pressedPoint == null || isWithinDeadZone(e)) {
        return
      }
      dragPane = findLayeredPane(e) ?: return
      dragButton = (parent as Stripe).getButtonFor(toolWindow.id)
      val image = ToolWindowDragHelper.createThumbnailDragImage(this)
      val dragButtonImage = object : JLabel(IconUtil.createImageIcon((image as Image))) {
        override fun toString() = "Image for: " + this@StripeButton
      }
      this.dragButtonImage = dragButtonImage
      dragButtonImage.addMouseListener(object : MouseAdapter() {
        override fun mouseReleased(e: MouseEvent) {
          finishDragging()
          pressedPoint = null
          this@StripeButton.dragButtonImage = null
          super.mouseReleased(e)
        }
      })
      dragPane!!.add(dragButtonImage, JLayeredPane.POPUP_LAYER as Any)
      dragButtonImage.size = dragButtonImage.preferredSize
      isVisible = false
      toolWindow.toolWindowManager.getToolWindowPane(toolWindow).buttonManager.startDrag()
      dragKeyEventDispatcher = DragKeyEventDispatcher()
      KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dragKeyEventDispatcher)
    }
    if (!isDraggingNow) {
      return
    }
    val xy = SwingUtilities.convertPoint(e.component, e.point, dragPane)
    if (pressedPoint != null) {
      xy.x -= pressedPoint!!.x
      xy.y -= pressedPoint!!.y
    }
    dragButtonImage!!.location = xy
    SwingUtilities.convertPointToScreen(xy, dragPane)
    val devicePoint = DevicePoint(xy, dragPane!!)
    val stripe = toolWindow.toolWindowManager.getToolWindowPane(toolWindow).getStripeFor(devicePoint, parent as Stripe)
    if (stripe == null) {
      if (lastStripe != null) {
        lastStripe!!.resetDrop()
      }
    }
    else {
      if (lastStripe != null && lastStripe !== stripe) {
        lastStripe!!.resetDrop()
      }
      stripe.processDropButton(dragButton!!, dragButtonImage!!, devicePoint)
    }
    lastStripe = stripe
  }

  private inner class DragKeyEventDispatcher : KeyEventDispatcher {
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
      if (isDraggingNow && e.keyCode == KeyEvent.VK_ESCAPE && e.id == KeyEvent.KEY_PRESSED) {
        dragCancelled = true
        finishDragging()
        return true
      }
      return false
    }
  }

  private fun isWithinDeadZone(e: MouseEvent): Boolean {
    return pressedPoint!!.distance(e.point) < JBUI.scale(MouseDragHelper.DRAG_START_DEADZONE)
  }

  override fun processMouseEvent(e: MouseEvent) {
    if (e.isPopupTrigger && e.component.isShowing) {
      super.processMouseEvent(e)
      return
    }
    if (UIUtil.isCloseClick(e)) {
      toolWindow.toolWindowManager.hideToolWindow(toolWindow.id, true)
      return
    }
    if (e.button == MouseEvent.BUTTON1) {
      if (MouseEvent.MOUSE_PRESSED == e.id) {
        pressedPoint = e.point
        pressedWhenSelected = isSelected
        dragCancelled = false
      }
      else if (MouseEvent.MOUSE_RELEASED == e.id) {
        finishDragging()
        pressedPoint = null
        dragButtonImage = null
      }
    }
    super.processMouseEvent(e)
  }

  private fun showPopup(component: Component?, x: Int, y: Int) {
    val group = toolWindow.createPopupGroup()
    val popupMenu = ActionManager.getInstance().createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, group)
    popupMenu.component.show(component, x, y)
  }

  override fun updateUI() {
    setUI(StripeButtonUi())
    val font = StartupUiUtil.labelFont
    val relativeFont = RelativeFont.NORMAL.fromResource("StripeButton.fontSizeOffset", -2, JBUIScale.scale(11f))
    setFont(relativeFont.derive(font))
  }

  fun updatePresentation() {
    updateState(toolWindow)
    updateText(toolWindow)
    updateIcon(toolWindow.getIcon())
  }

  fun updateIcon(icon: Icon?) {
    setIcon(icon)
    disabledIcon = if (icon == null) null else IconLoader.getDisabledIcon(icon)
  }

  private fun updateText(toolWindow: ToolWindowImpl) {
    var text = toolWindow.stripeTitle
    if (UISettings.getInstance().showToolWindowsNumbers) {
      val mnemonic = ActivateToolWindowAction.Manager.getMnemonicForToolWindow(toolWindow.id)
      if (mnemonic != -1) {
        text = mnemonic.toChar().toString() + ": " + text
        setMnemonic2(mnemonic)
      }
      else {
        setMnemonic2(0)
      }
    }
    setText(text)
    updateHelpTooltip()
  }

  internal fun updateState(toolWindow: ToolWindowImpl) {
    val toShow = toolWindow.isAvailable || toolWindow.isPlaceholderMode
    isVisible = toShow && (toolWindow.isShowStripeButton || isSelected)
    isEnabled = toolWindow.isAvailable
  }

  private val isDraggingNow: Boolean
    get() = dragButtonImage != null

  private fun finishDragging() {
    if (!isDraggingNow) {
      return
    }
    dragPane!!.remove(dragButtonImage)
    dragButtonImage = null
    dragButton = null
    toolWindow.toolWindowManager.getToolWindowPane(toolWindow).buttonManager.stopDrag()
    dragPane!!.repaint()
    isVisible = true
    if (lastStripe != null) {
      lastStripe!!.finishDrop(toolWindow.toolWindowManager)
      lastStripe = null
    }
    if (dragKeyEventDispatcher != null) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dragKeyEventDispatcher)
      dragKeyEventDispatcher = null
    }
  }

  override fun toString(): String {
    return "${StringUtilRt.getShortName(javaClass.name)} text: $text"
  }
}

private fun findLayeredPane(e: MouseEvent): JLayeredPane? {
  if (e.component !is JComponent) {
    return null
  }
  return (e.component as JComponent).rootPane.layeredPane
}