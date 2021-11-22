// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowAnchor.*
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.paint.RectanglePainter
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.lang.ref.WeakReference
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.SwingUtilities

internal class ToolWindowDragHelper(parent: @NotNull Disposable,
                                    val pane: @NotNull ToolWindowsPane) : MouseDragHelper<ToolWindowsPane>(parent, pane) {
  private var toolWindowRef = null as WeakReference<ToolWindowImpl?>?
  private var myInitialAnchor = null as ToolWindowAnchor?
  private var myInitialButton = null as StripeButton?
  private var myInitialSize = Dimension()
  private var myLastStripe = null as Stripe?

  private var myDialog = null as MyDialog?
  private val myHighlighter = createHighlighterComponent()
  private val myInitialOffset = Point()
  private var mySourceIsHeader = false

  companion object {
    const val THUMB_SIZE = 220
    const val THUMB_OPACITY = .85f

    @Nullable
    fun createDragImage(component: JComponent, thumbSize: Int = JBUI.scale(THUMB_SIZE)): BufferedImage {
      val image = UIUtil.createImage(component, component.width, component.height, BufferedImage.TYPE_INT_RGB)
      val graphics = image.graphics
      graphics.color = UIUtil.getBgFillColor(component)
      RectanglePainter.FILL.paint(graphics as @NotNull Graphics2D, 0, 0, component.width, component.height, null)
      component.paint(graphics)
      graphics.dispose()
      val width: Double = image.getWidth(null).toDouble()
      val height: Double = image.getHeight(null).toDouble()
      if (thumbSize == -1 || width <= thumbSize && height <= thumbSize) return image
      val ratio: Double = if (width > height) {
        thumbSize / width
      }
      else {
        thumbSize / height
      }
      return ImageUtil.scaleImage(image, (width * ratio).toInt(), (height * ratio).toInt()) as BufferedImage
    }

    fun createHighlighterComponent() = object: NonOpaquePanel() {
      override fun paint(g: Graphics) {
        g.color = JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND
        g.fillRect(0, 0, width, height)
      }
    }
  }

  override fun isDragOut(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point): Boolean {
    return isDragOut(dragToScreenPoint)
  }

  private fun isDragOut(dragToScreenPoint: Point): Boolean {
    val toolWindow = getToolWindow()
    if (toolWindow != null && toolWindow.getBoundsOnScreen(toolWindow.anchor, dragToScreenPoint).contains(dragToScreenPoint)) {
      return false
    }
    return myLastStripe == null
  }

  override fun canStartDragging(dragComponent: JComponent, dragComponentPoint: Point): Boolean {
    return getToolWindow(RelativePoint(dragComponent, dragComponentPoint)) != null
  }

  fun getToolWindow(startScreenPoint: RelativePoint): ToolWindowImpl? {
    val p = startScreenPoint.getPoint(pane)
    val clickedComponent = SwingUtilities.getDeepestComponentAt(pane, p.x, p.y)
    if (clickedComponent != null && isComponentDraggable(clickedComponent)) {
      val decorator = InternalDecoratorImpl.findNearestDecorator(clickedComponent)
      if (decorator != null &&
          (decorator.toolWindow.anchor != BOTTOM ||
           decorator.locationOnScreen.y < startScreenPoint.screenPoint.y - ToolWindowsPane.getHeaderResizeArea()))
        return decorator.toolWindow
    }
    if (clickedComponent is StripeButton) {
      return clickedComponent.toolWindow
    }
    return null
  }

  override fun processMousePressed(event: MouseEvent) {
    val relativePoint = RelativePoint(event)
    val toolWindow = getToolWindow(relativePoint)
    if (toolWindow == null) {
      return
    }
    val decorator = if (toolWindow.isVisible) toolWindow.decorator else null
    toolWindowRef = WeakReference(toolWindow)
    myInitialAnchor = toolWindow.anchor
    myLastStripe = pane.getStripeFor(toolWindow.anchor)
    myInitialButton = pane.getStripeFor(toolWindow.anchor).getButtonFor(toolWindow.id)
    myInitialSize = toolWindow.getBoundsOnScreen(toolWindow.anchor, relativePoint.screenPoint).size
    val dragOutImage = if (decorator != null && !decorator.bounds.isEmpty) createDragImage(decorator) else null
    val dragImage = if (myInitialButton != null) myInitialButton!!.createDragImage() else dragOutImage
    val point = relativePoint.getPoint(pane)
    val component = SwingUtilities.getDeepestComponentAt(pane, point.x, point.y)
    mySourceIsHeader = true
    if (component is StripeButton) {
      myInitialOffset.location = relativePoint.getPoint(component)
      mySourceIsHeader = false
    }
    else if (dragImage != null) {
      myInitialOffset.location = Point(dragImage.getWidth(pane) / 4, dragImage.getHeight(pane) / 4)
    }
    if (dragImage != null) {
      myDialog = MyDialog(pane, this, dragImage, dragOutImage)
    }
  }

  override fun mouseReleased(e: MouseEvent?) {
    if (getToolWindow() == null) return
    super.mouseReleased(e)
    stopDrag()
  }

  override fun processDragOut(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point, justStarted: Boolean) {
    if (getToolWindow() == null) return
    relocate(event)
    event.consume()
  }

  override fun processDragFinish(event: MouseEvent, willDragOutStart: Boolean) {
    if (getToolWindow() == null) return
    if (willDragOutStart) {
      setDragOut(true)
      return
    }
    val screenPoint = event.locationOnScreen
    val window = getToolWindow()
    if (window == null || !willDragOutStart
        && pane.getStripeFor(screenPoint, pane.getStripeFor(myInitialAnchor!!)) == null
        && window.getBoundsOnScreen(myInitialAnchor!!, screenPoint).contains(screenPoint)) {
      cancelDragging()
      return
    }

    val stripe = myLastStripe
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
            val decorator = InternalDecoratorImpl.findTopLevelDecorator(window.component)
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

  private fun getToolWindow(): ToolWindowImpl? {
    return toolWindowRef?.get()
  }

  override fun processDragOutFinish(event: MouseEvent) {
    processDragFinish(event, false)
  }

  override fun cancelDragging(): Boolean {
    if (super.cancelDragging()) {
      stopDrag()
      return true
    }
    return false
  }

  override fun stop() {
    super.stop()
    stopDrag()
  }

  private fun stopDrag() {
    val window = getToolWindow()
    getStripeButton(window)?.isVisible = true
    pane.stopDrag()
    with(pane.rootPane.glassPane as JComponent) {
      remove(myHighlighter)
      revalidate()
      repaint()
    }

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
  }

  override fun processDrag(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point) {
    if (!checkModifiers(event)) return
    if (isDragJustStarted) {
      startDrag(event)
    }
    else {
      relocate(event)
    }
  }

  override fun getDragStartDeadzone(pressedScreenPoint: Point , draggedScreenPoint: Point): Int {
    val point = RelativePoint(pressedScreenPoint).getPoint(pane)
    if (SwingUtilities.getDeepestComponentAt(pane, point.x, point.y) is StripeButton) {
      return super.getDragStartDeadzone(pressedScreenPoint, draggedScreenPoint)
    }
    return JBUI.scale(Registry.intValue("ide.new.tool.window.start.drag.deadzone", 7, 0, 100))
  }

  private fun startDrag(event: MouseEvent) {
    relocate(event)
    if (myInitialButton != null) {
      myInitialButton!!.isVisible = false
    }
    myDialog?.isVisible = true
    with(pane.rootPane.glassPane as JComponent) {
      add(myHighlighter)
      revalidate()
      repaint()
    }
    pane.startDrag()
  }

  private fun ToolWindowImpl.getBoundsOnScreen(anchor: ToolWindowAnchor, screenPoint : Point): Rectangle {
    val trueStripe = pane.getStripeFor(screenPoint, pane.getStripeFor(myInitialAnchor!!))
    val location = pane.locationOnScreen
    location.x += getStripeWidth(LEFT)
    location.y += getStripeHeight(TOP)
    var width = pane.width - getStripeWidth(LEFT) - getStripeWidth(RIGHT)
    var height = pane.height - getStripeHeight(TOP) - getStripeHeight(BOTTOM)
    if (anchor === myInitialAnchor  && trueStripe == null && isVisible) {
      if (anchor.isHorizontal) height = (pane.rootPane.height * windowInfo.weight).toInt()
      else width = (pane.rootPane.width * windowInfo.weight).toInt()
    } else {
      if (anchor.isHorizontal) height = Stripe.DROP_DISTANCE_SENSITIVITY
      else width = Stripe.DROP_DISTANCE_SENSITIVITY
    }

    when (anchor) {
      BOTTOM -> {
        location.y = pane.locationOnScreen.y + pane.height - height - getStripeHeight(BOTTOM)
      }
      RIGHT -> {
        location.x = pane.locationOnScreen.x + pane.width - width - getStripeWidth(RIGHT)
      }
    }
    return Rectangle(location.x, location.y, width, height)
  }

  private fun getStripeWidth(anchor: ToolWindowAnchor): Int {
    with(pane.getStripeFor(anchor)) {
      return if (isVisible && isShowing) width else 0
    }
  }

  private fun getStripeHeight(anchor: ToolWindowAnchor): Int {
    with(pane.getStripeFor(anchor)) {
      return if (isVisible && isShowing) height else 0
    }
  }

  private fun getStripeButton(window: ToolWindowImpl?): StripeButton? {
    if (window == null) return null
    return pane.getStripeFor(window.anchor).getButtonFor(window.id)
  }

  private fun relocate(event: MouseEvent) {
    val screenPoint = event.locationOnScreen
    val dialog = myDialog
    val toolWindow = getToolWindow()
    if (dialog == null || toolWindow == null) return

    val dragOut = isDragOut(screenPoint)
    dialog.setLocation(screenPoint.x - myInitialOffset.x, screenPoint.y - myInitialOffset.y)
    setDragOut(dragOut)

    val stripe = getStripe(screenPoint)

    if (myLastStripe != null && myLastStripe != stripe) {
      myLastStripe!!.resetDrop()
    }
    myHighlighter.isVisible = stripe != null
    if (stripe != null && myInitialButton != null) {
      //if (myLastStripe != stripe) {//attempt to rotate thumb image on fly to show actual button view if user drops it right here
      //  val button = object : StripeButton(pane, toolWindow) {
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
      SwingUtilities.invokeLater(Runnable {
        val bounds = toolWindow.getBoundsOnScreen(stripe.anchor, screenPoint)
        val p = bounds.location
        SwingUtilities.convertPointFromScreen(p, pane.rootPane.layeredPane)
        bounds.location = p
        val dropToSide = stripe.dropToSide
        if (dropToSide != null) {
          val half = if (stripe.anchor.isHorizontal) bounds.width / 2 else bounds.height / 2
          if (!stripe.anchor.isHorizontal) {
            bounds.height -= half
            if (dropToSide) {
              bounds.y += half
            }
          } else {
            bounds.width -= half
            if (dropToSide) {
              bounds.x += half
            }
          }
        }
        myHighlighter.bounds = bounds
      })
    }
    myLastStripe = stripe
  }

  private fun getStripe(screenPoint: Point): Stripe? {
    var stripe = pane.getStripeFor(screenPoint, pane.getStripeFor(myInitialAnchor!!))
    if (stripe == null && getToolWindow()!!.getBoundsOnScreen(myInitialAnchor!!, screenPoint).contains(screenPoint)) {
      stripe = pane.getStripeFor(myInitialAnchor!!)
    }
    if (stripe?.anchor == TOP) return null
    return stripe
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
      try {
        opacity = THUMB_OPACITY
      }
      catch (ignored: Exception) {
      }
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
        icon = IconUtil.createImageIcon(image as Image)
        revalidate()
        pack()
        repaint()
      }
    }
  }
}