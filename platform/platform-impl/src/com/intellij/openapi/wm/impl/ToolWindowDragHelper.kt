// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.TimerListener
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowAnchor.*
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.ui.ComponentUtil
import com.intellij.ui.JBColor
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.lang.ref.WeakReference
import javax.swing.*

internal class ToolWindowDragHelper(parent: @NotNull Disposable,
                                    toolWindowsPane: @NotNull ToolWindowsPane) : MouseDragHelper<ToolWindowsPane>(parent,
                                                                                                                  toolWindowsPane), TimerListener {
  private val myPane = myDragComponent

  private var toolWindowRef = null as WeakReference<ToolWindowImpl?>?
  private var myInitialAnchor = null as ToolWindowAnchor?
  private var myInitialButton = null as StripeButton?
  private var myInitialSize = Dimension()
  private var myLastStripe = null as Stripe?

  private var myDialog = null as MyDialog?
  private val myHighlighter = MyAreaHighlighter()
  private val myInitialOffset = Point()
  private var mySourceIsHeader = false

  companion object {
    val THUMB_SIZE = 220
    val THUMB_OPACITY = .85f
  }


  override fun getModalityState(): ModalityState {
    return ModalityState.NON_MODAL
  }

  override fun run() {
    if (getToolWindow() != null)
      SwingUtilities.invokeLater {
        myHighlighter.revalidate()
        myHighlighter.repaint()
      }
  }

  override fun isDragOut(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point): Boolean {
    return isDragOut(dragToScreenPoint)
  }

  private fun isDragOut(dragToScreenPoint: Point): Boolean {
    val toolWindow = getToolWindow()
    if (toolWindow != null && toolWindow.getBoundsOnScreen(toolWindow.anchor).contains(dragToScreenPoint)) {
      return false
    }
    return myLastStripe == null
  }

  override fun canStartDragging(dragComponent: JComponent, dragComponentPoint: Point): Boolean {
    return getToolWindow(RelativePoint(dragComponent, dragComponentPoint)) != null
  }

  fun getToolWindow(startScreenPoint: RelativePoint): ToolWindowImpl? {
    val decorators = ArrayList(ComponentUtil.findComponentsOfType(myPane, InternalDecoratorImpl::class.java))
    for (decorator in decorators) {
      val bounds = decorator.headerScreenBounds
      if (bounds != null && bounds.contains(startScreenPoint.screenPoint)) {
        val point = startScreenPoint.getPoint(decorator)
        val child = SwingUtilities.getDeepestComponentAt(decorator, point.x, point.y)
        if (child.parent is ToolWindowHeader) {
          if (decorator.toolWindow.anchor != BOTTOM || decorator.locationOnScreen.y <= startScreenPoint.screenPoint.y - ToolWindowsPane.HEADER_RESIZE_WIDTH)
            return decorator.toolWindow
        }
      }
    }
    val point = startScreenPoint.getPoint(myPane)
    val component = SwingUtilities.getDeepestComponentAt(myPane, point.x, point.y)
    if (component is StripeButton) {
      return component.toolWindow
    }
    return null
  }

  override fun processMousePressed(event: MouseEvent) {
    val relativePoint = RelativePoint(event)
    val toolWindow = getToolWindow(relativePoint);
    if (toolWindow == null) {
      return
    }
    val decorator = if (toolWindow.isVisible) toolWindow.decorator else null
    toolWindowRef = WeakReference(toolWindow)
    myInitialAnchor = toolWindow.anchor
    myLastStripe = myPane.getStripeFor(toolWindow.anchor)
    myInitialButton = myPane.getStripeFor(toolWindow.anchor).getButtonFor(toolWindow.id)
    myInitialSize = toolWindow.getBoundsOnScreen(toolWindow.anchor).size
    val dragOutImage = if (decorator != null) createDragImage(decorator) else null
    val dragImage = if (myInitialButton != null) myInitialButton!!.createDragImage(event) else dragOutImage
    val point = relativePoint.getPoint(myPane)
    val component = SwingUtilities.getDeepestComponentAt(myPane, point.x, point.y)
    mySourceIsHeader = true
    if (component is StripeButton) {
      myInitialOffset.location = relativePoint.getPoint(component)
      mySourceIsHeader = false
    }
    else if (dragImage != null) {
      myInitialOffset.location = Point(dragImage.getWidth(myPane) / 2, dragImage.getHeight(myPane) / 2)
    }
    if (dragImage != null) {
      myDialog = MyDialog(myPane, this, dragImage, dragOutImage)
    }
  }

  override fun mouseReleased(e: MouseEvent?) {
    super.mouseReleased(e)
    stopDrag()
  }

  @Nullable
  private fun createDragImage(component: JComponent): BufferedImage {
    val image = UIUtil.createImage(component, component.width, component.height, BufferedImage.TRANSLUCENT)
    val graphics = image.graphics
    component.paint(graphics)
    graphics.dispose()
    val width: Double = image.getWidth(null).toDouble()
    val height: Double = image.getHeight(null).toDouble()
    if (width <= THUMB_SIZE && height <= THUMB_SIZE) return image
    val ratio: Double
    ratio = if (width > height) {
      THUMB_SIZE / width
    }
    else {
      THUMB_SIZE / height
    }
    return ImageUtil.scaleImage(image, (width * ratio).toInt(), (height * ratio).toInt()) as BufferedImage
  }

  override fun processDragOut(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point, justStarted: Boolean) {
    relocate(event)
    event.consume()
  }

  override fun processDragFinish(event: MouseEvent, willDragOutStart: Boolean) {
    if (willDragOutStart) {
      setDragOut(true)
      return
    }
    val window = getToolWindow()
    val stripe = myLastStripe
    if (window != null) {
      if (stripe != null) {
        stripe.finishDrop(window.toolWindowManager)
      }
      else {
        window.toolWindowManager.setToolWindowType(window.id, ToolWindowType.FLOATING)
        window.toolWindowManager.activateToolWindow(window.id, Runnable {
          val w = UIUtil.getWindow(window.component)
          if (w is JDialog) {
            val locationOnScreen = event.locationOnScreen
            if (mySourceIsHeader) {
              val decorator = ComponentUtil.getParentOfType(InternalDecoratorImpl::class.java, window.component)
              if (decorator != null) {
                val shift = SwingUtilities.convertPoint(decorator, decorator.location, w)
                locationOnScreen.translate(-shift.x, -shift.y)
              }
              locationOnScreen.translate(-myInitialOffset.x, -myInitialOffset.y)
            }
            w.location = locationOnScreen
            val bounds = w.bounds
            bounds.size = myInitialSize
            ScreenUtil.fitToScreen(bounds)
            w.bounds = bounds
          }
        }, true, null)
      }
    }
    stopDrag()
  }

  private fun getToolWindow(): ToolWindowImpl? {
    return toolWindowRef?.get()
  }

  override fun processDragOutFinish(event: MouseEvent) {
    processDragFinish(event, false)
  }

  override fun cancelDragging(): Boolean {
    super.cancelDragging()
    stopDrag()
    return true
  }

  override fun stop() {
    super.stop()
    stopDrag()
  }

  private fun stopDrag() {
    val window = getToolWindow()
    getStripeButton(window)?.isVisible = true
    myPane.stopDrag()
    myPane.rootPane.layeredPane.remove(myHighlighter)
    myPane.rootPane.layeredPane.repaint()

    @Suppress("SSBasedInspection")
    myDialog?.dispose()
    myDialog = null
    toolWindowRef = null
    myInitialAnchor = null
    if (myLastStripe != null) {
      myLastStripe!!.resetDrop()
      myLastStripe = null
    }
    myInitialButton = null
    ActionManager.getInstance()?.removeTimerListener(this)
  }

  override fun processDrag(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point) {
    if (isDragJustStarted) {
      startDrag(event)
    }
    else {
      relocate(event)
    }
  }

  private fun startDrag(event: MouseEvent) {
    relocate(event)
    if (myInitialButton != null) {
      myInitialButton!!.isVisible = false
    }
    myDialog?.isVisible = true
    myPane.rootPane.layeredPane.add(myHighlighter, Integer.valueOf(JLayeredPane.POPUP_LAYER + 1))
    myPane.startDrag()
    ActionManager.getInstance().addTimerListener(0, this)
  }

  private fun ToolWindowImpl.getBoundsOnScreen(anchor: ToolWindowAnchor): Rectangle {
    val location = myPane.locationOnScreen
    location.x += getStripeWidth(LEFT)
    location.y += getStripeHeight(TOP)
    var width = myPane.width - getStripeWidth(LEFT) - getStripeWidth(RIGHT)
    var height = myPane.height - getStripeHeight(TOP) - getStripeHeight(BOTTOM)
    val weight = windowInfo.weight //todo: maybe limit weight for highlighter

    if (anchor.isHorizontal) height = (myPane.rootPane.height * weight).toInt()
    else width = (myPane.rootPane.width * weight).toInt()

    when (anchor) {
      BOTTOM -> {
        location.y = myPane.locationOnScreen.y + myPane.height - height - getStripeHeight(BOTTOM)
      }
      RIGHT -> {
        location.x = myPane.locationOnScreen.x + myPane.width - width - getStripeWidth(RIGHT)
      }
    }
    return Rectangle(location.x, location.y, width, height)
  }

  private fun getStripeWidth(anchor: ToolWindowAnchor): Int {
    with(myPane.getStripeFor(anchor)) {
      return if (isVisible && isShowing) width else 0
    }
  }

  private fun getStripeHeight(anchor: ToolWindowAnchor): Int {
    with(myPane.getStripeFor(anchor)) {
      return if (isVisible && isShowing) height else 0
    }
  }

  private fun getStripeButton(window: ToolWindowImpl?): StripeButton? {
    if (window == null) return null
    return myPane.getStripeFor(window.anchor).getButtonFor(window.id)
  }

  private fun relocate(event: MouseEvent) {
    val screenPoint = event.locationOnScreen
    val dialog = myDialog
    val toolWindow = getToolWindow()
    if (dialog == null || toolWindow == null) return

    val dragOut = isDragOut(screenPoint)
    dialog.setLocation(screenPoint.x - myInitialOffset.x, screenPoint.y - myInitialOffset.y)
    setDragOut(dragOut)

    var stripe = myPane.getStripeFor(Rectangle(screenPoint, /*Dimension(dialog.width, dialog.height)*/Dimension(1, 1)),
                                     myPane.getStripeFor(myInitialAnchor!!))
    val fallbackBounds = toolWindow.getBoundsOnScreen(myInitialAnchor!!)
    if (stripe == null && fallbackBounds.contains(screenPoint)) {
      stripe = myPane.getStripeFor(myInitialAnchor!!) //fallback
    }

    if (myLastStripe != null && myLastStripe != stripe) {
      myLastStripe!!.resetDrop()
    }
    myHighlighter.isVisible = stripe != null
    if (stripe != null && myInitialButton != null) {
      //if (myLastStripe != stripe) {
      //  val button = object : StripeButton(myPane, toolWindow) {
      //    override fun getAnchor(): ToolWindowAnchor {
      //      return stripe.anchor
      //    }
      //  }
      //  val info = (toolWindow.windowInfo as WindowInfoImpl).copy()
      //  //info.anchor = stripe.anchor
      //  button.apply(info)
      //  button.updatePresentation()
      //  button.size = button.preferredSize
      //  stripe.add(button)
      //  val image = button.createDragImage(event)
      //  dialog.updateIcon(image)
      //  stripe.remove(button)
      //}
      stripe.processDropButton(myInitialButton as StripeButton, dialog.contentPane as @NotNull JComponent, screenPoint)
      val bounds = toolWindowRef?.get()?.getBoundsOnScreen(stripe.anchor)
      if (bounds != null) {
        val p = bounds.location
        SwingUtilities.convertPointFromScreen(p, myPane.rootPane.layeredPane)
        bounds.location = p
        myHighlighter.bounds = bounds
      }
      else {
        myHighlighter.bounds = Rectangle()
      }
      myHighlighter.repaint()
    }
    myLastStripe = stripe
  }

  private fun setDragOut(dragOut: Boolean) {
    myDialog?.setDragOut(dragOut)
    myHighlighter.isVisible = !dragOut
  }


  class MyDialog(owner: JComponent,
                 val helper: ToolWindowDragHelper,
                 val stripeButtonImage: BufferedImage,
                 val toolWindowThumbnailImage: BufferedImage?) : JDialog(
    UIUtil.getWindow(owner), null, ModalityType.MODELESS) {
    var myDragOut = null as Boolean?

    init {
      isUndecorated = true
      opacity = THUMB_OPACITY
      isAlwaysOnTop = true
      contentPane = JLabel()
      contentPane.addMouseListener(object : MouseAdapter() {
        override fun mouseReleased(e: MouseEvent?) {
          helper.mouseReleased(e) //stop drag
        }

        override fun mouseDragged(e: MouseEvent?) {
          helper.relocate(e!!)
        }
      })
      setDragOut(false)
    }

    fun setDragOut(dragOut: Boolean) {
      if (dragOut == myDragOut) return
      myDragOut = dragOut
      val image = if (dragOut && toolWindowThumbnailImage != null) toolWindowThumbnailImage else stripeButtonImage
      updateIcon(image)
    }

    fun updateIcon(image: BufferedImage) {
      with(contentPane as JLabel) {
        icon = IconUtil.createImageIcon(image)
        revalidate()
        pack()
        repaint()
      }
    }
  }

  class MyAreaHighlighter : NonOpaquePanel() {
    override fun paint(g: Graphics) {
      g.color = JBColor.namedColor("DragAndDrop.areaBackground", 0x3d7dcc, 0x404a57)
      g.fillRect(0, 0, width, height)
    }
  }
}