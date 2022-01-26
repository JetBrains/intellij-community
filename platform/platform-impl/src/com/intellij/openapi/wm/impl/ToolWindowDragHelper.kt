// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowAnchor.*
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.paint.RectanglePainter
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBInsets
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

fun Dimension.isNotEmpty():Boolean = width > 0 && height > 0

fun Component.createDragImage(): BufferedImage? {
  val initialBounds = bounds
  return try {
    if (initialBounds.isEmpty) {
      size = preferredSize
    }

    val areaSize = when (this) {
      is StripeButton -> size.also {
        val delta = JBUIScale.scale(1)
        it.width -= delta
        it.height -= delta
      }

      is SquareStripeButton -> size.also {
        JBInsets.removeFrom(it, insets)
        JBInsets.removeFrom(it, SquareStripeButtonLook.ICON_PADDING)
      }

      else -> JBUI.emptySize()
    }

    if (areaSize.isNotEmpty())
      UIUtil.createImage(this, areaSize.width, areaSize.height, BufferedImage.TYPE_INT_RGB).also { image->
        val graphics = image.graphics
        graphics.color = if (ExperimentalUI.isNewToolWindowsStripes()) JBUI.CurrentTheme.ToolWindow.DragAndDrop.BUTTON_FLOATING_BACKGROUND
        else UIUtil.getBgFillColor(parent)

        graphics.fillRect(0, 0, areaSize.width, areaSize.height)

        when (this) {
          is StripeButton -> paint(graphics)
          is SquareStripeButton -> paintDraggingButton(graphics)
        }

        graphics.dispose()
      }
    else null
  }
  finally {
    bounds = initialBounds
  }
}

internal class ToolWindowDragHelper(parent: @NotNull Disposable,
                                    val pane: @NotNull ToolWindowsPane) : MouseDragHelper<ToolWindowsPane>(parent, pane) {
  private var toolWindowRef : WeakReference<ToolWindowImpl?>? = null
  private var myInitialAnchor : ToolWindowAnchor? = null
  private var myInitialButton : Component? = null
  private var myInitialSize = Dimension()
  private var myLastStripe : AbstractDroppableStripe? = null

  private var myDialog : MyDialog? = null
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
    if (ExperimentalUI.isNewToolWindowsStripes()) {
      return getStripe(dragToScreenPoint) == null
    }
    else {
      val toolWindow = getToolWindow()
      if (toolWindow != null && toolWindow.getBoundsOnScreen(toolWindow.anchor, dragToScreenPoint).contains(dragToScreenPoint)) {
        return false
      }
      return myLastStripe == null
    }
  }

  override fun canStartDragging(dragComponent: JComponent, dragComponentPoint: Point): Boolean {
    return getToolWindow(RelativePoint(dragComponent, dragComponentPoint)) != null
  }

  private fun getComponentAtPoint(point: RelativePoint) : Component? =
    point.getPoint(pane).let { SwingUtilities.getDeepestComponentAt(pane, it.x, it.y) } ?:
    point.getPoint(pane.parent).let { SwingUtilities.getDeepestComponentAt(pane.parent, it.x, it.y) }

  fun getToolWindow(startScreenPoint: RelativePoint): ToolWindowImpl? {
    val clickedComponent = getComponentAtPoint(startScreenPoint)
    if (clickedComponent != null && isComponentDraggable(clickedComponent)) {
      val decorator = InternalDecoratorImpl.findNearestDecorator(clickedComponent)
      if (decorator != null &&
          (decorator.toolWindow.anchor != BOTTOM ||
           decorator.locationOnScreen.y < startScreenPoint.screenPoint.y - ToolWindowsPane.getHeaderResizeArea()))
        return decorator.toolWindow
    }

    return when (clickedComponent) {
      is StripeButton -> clickedComponent.toolWindow
      is SquareStripeButton -> clickedComponent.button.toolWindow
      else -> null
    }
  }

  override fun processMousePressed(event: MouseEvent) {
    val relativePoint = RelativePoint(event)
    val toolWindow = getToolWindow(relativePoint)
    if (toolWindow == null) {
      return
    }
    val component = getComponentAtPoint(relativePoint)
    val decorator = if (toolWindow.isVisible) toolWindow.decorator else null
    toolWindowRef = WeakReference(toolWindow)
    if (ExperimentalUI.isNewToolWindowsStripes()) {
      myInitialAnchor = toolWindow.largeStripeAnchor
      myLastStripe = component?.let { c ->
        val parent = c.parent
        if (c.isShowing && parent is AbstractDroppableStripe) {
          myInitialButton = c
          myInitialSize = toolWindow.windowInfo.floatingBounds?.size ?: JBUI.size(500, 400)
          parent
        }
        else null
      }
    }
    else {
      myInitialAnchor = toolWindow.anchor
      myLastStripe = pane.getStripeFor(toolWindow.anchor).also {
        myInitialButton = if (it is Stripe && it.isShowing) it.getButtonFor(toolWindow.id) else component
      }

      myInitialSize = toolWindow.getBoundsOnScreen(toolWindow.anchor, relativePoint.screenPoint).size
    }

    val dragOutImage = if (decorator != null && !decorator.bounds.isEmpty) createDragImage(decorator) else null
    val dragImage = myInitialButton?.createDragImage() ?: dragOutImage
    mySourceIsHeader = true

    if (component is StripeButton || component is SquareStripeButton) {
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
    val toolWindow = getToolWindow() ?: return
    if (willDragOutStart) {
      setDragOut(true)
      return
    }

    val screenPoint = event.locationOnScreen
    val initialStripe = pane.getStripeFor(myInitialAnchor!!)
    if (pane.getStripeFor(screenPoint, initialStripe) == null &&
        toolWindow.getBoundsOnScreen(myInitialAnchor!!, screenPoint).contains(screenPoint)) {
      cancelDragging()
      return
    }

    val stripe = myLastStripe
    if (stripe != null) {
      stripe.finishDrop(toolWindow.toolWindowManager)
    }
    else {
      toolWindow.toolWindowManager.setToolWindowType(toolWindow.id, ToolWindowType.FLOATING)
      toolWindow.toolWindowManager.activateToolWindow(toolWindow.id, Runnable {
        val w = UIUtil.getWindow(toolWindow.component)
        if (w is JDialog) {
          val locationOnScreen = event.locationOnScreen
          if (mySourceIsHeader) {
            val decorator = InternalDecoratorImpl.findTopLevelDecorator(toolWindow.component)
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

      if (ExperimentalUI.isNewToolWindowsStripes()) {
        val info = toolWindow.toolWindowManager.layout.getInfo(toolWindow.id)
        toolWindow.toolWindowManager.setLargeStripeAnchor(toolWindow.id, initialStripe.getAnchor(), info?.orderOnLargeStripe ?: -1, true)
      }
    }
  }

  private fun getToolWindow() = toolWindowRef?.get()

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
    val component = getComponentAtPoint(RelativePoint(pressedScreenPoint))
    if (component is StripeButton || component is SquareStripeButton) {
      return super.getDragStartDeadzone(pressedScreenPoint, draggedScreenPoint)
    }
    return JBUI.scale(Registry.intValue("ide.new.tool.window.start.drag.deadzone", 7, 0, 100))
  }

  private fun startDrag(event: MouseEvent) {
    relocate(event)
    myInitialButton?.isVisible = false
    myDialog?.isVisible = true
    with(pane.rootPane.glassPane as JComponent) {
      add(myHighlighter)
      revalidate()
      repaint()
    }
    pane.startDrag()
  }

  private fun ToolWindowImpl.getBoundsOnScreen(anchor: ToolWindowAnchor, screenPoint : Point) =
    if (!ExperimentalUI.isNewToolWindowsStripes()){
      val location = pane.locationOnScreen
      var width = pane.width
      var height = pane.height

      location.x += getStripeWidth(LEFT)
      location.y += getStripeHeight(TOP)
      width -= getStripeWidth(LEFT) + getStripeWidth(RIGHT)
      height -= getStripeHeight(TOP) + getStripeHeight(BOTTOM)

      val trueStripe = pane.getStripeFor(screenPoint, pane.getStripeFor(myInitialAnchor!!))
      if (anchor === myInitialAnchor  && trueStripe == null && isVisible) {
        if (anchor.isHorizontal) height = (pane.rootPane.height * windowInfo.weight).toInt()
        else width = (pane.rootPane.width * windowInfo.weight).toInt()
      }
      else {
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
      Rectangle(location.x, location.y, width, height)
    }
    else Rectangle()


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

  private fun getStripeButton(window: ToolWindowImpl?): JComponent? =
    window?.let{ pane.getStripeFor(it.anchor).getButtonFor(it.id) }

  private fun relocate(event: MouseEvent) {
    val screenPoint = event.locationOnScreen
    val dialog = myDialog
    val toolWindow = getToolWindow()
    if (dialog == null || toolWindow == null) return

    val dragOut = isDragOut(screenPoint)
    dialog.setLocation(screenPoint.x - myInitialOffset.x, screenPoint.y - myInitialOffset.y)
    setDragOut(dragOut)

    val stripe = getStripe(screenPoint)
    myLastStripe?.let { if (it != stripe) it.resetDrop() }

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
      stripe.processDropButton(myInitialButton as JComponent, dialog.contentPane as JComponent, screenPoint)
      if (!ExperimentalUI.isNewToolWindowsStripes()) {
        SwingUtilities.invokeLater(Runnable {
          if (myInitialAnchor == null) return@Runnable
          val bounds = toolWindow.getBoundsOnScreen(stripe.getAnchor(), screenPoint)
          val p = bounds.location
          SwingUtilities.convertPointFromScreen(p, pane.rootPane.layeredPane)
          bounds.location = p
          val dropToSide = stripe.getDropToSide()
          if (dropToSide != null) {
            val half = if (stripe.getAnchor().isHorizontal) bounds.width / 2 else bounds.height / 2
            if (!stripe.getAnchor().isHorizontal) {
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
    }
    myLastStripe = stripe
  }

  private fun getStripe(screenPoint: Point): AbstractDroppableStripe? {
    val stripe = myInitialAnchor?.let { anchor ->
      val preferred = pane.getStripeFor(anchor)
      pane.getStripeFor(screenPoint, preferred) ?:
      if (getToolWindow()!!.getBoundsOnScreen(anchor, screenPoint).contains(screenPoint)) preferred else null
    }
    return if (stripe?.getAnchor() == TOP) null else stripe
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