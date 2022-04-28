// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowAnchor.*
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.impl.AbstractDroppableStripe
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.openapi.wm.impl.SquareStripeButtonLook
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.ui.ComponentUtil
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
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.lang.ref.WeakReference
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.SwingUtilities

private fun Dimension.isNotEmpty(): Boolean = width > 0 && height > 0
private const val THUMB_SIZE = 220

internal class ToolWindowDragHelper(parent: Disposable, @JvmField val dragSourcePane: ToolWindowPane) : MouseDragHelper<ToolWindowPane>(parent, dragSourcePane) {
  private val isNewUi = dragSourcePane.buttonManager.isNewUi
  private val dropTargetHighlightComponent = createDropTargetHighlightComponent()

  private var toolWindowRef: WeakReference<ToolWindowImpl?>? = null
  private var initialAnchor: ToolWindowAnchor? = null
  private var initialButton: Component? = null
  private val initialOffset = Point()
  private val floatingWindowSize = Dimension()
  private var lastStripe: AbstractDroppableStripe? = null
  private var dragImageDialog: DragImageDialog? = null
  private var sourceIsHeader = false

  companion object {
    const val THUMB_OPACITY = .85f

    /**
     * Create a potentially scaled image of the component to use as a drag image
     */
    internal fun createThumbnailDragImage(component: JComponent, thumbSize: Int = JBUI.scale(THUMB_SIZE)): BufferedImage {
      val image = ImageUtil.createImage(component.graphicsConfiguration, component.width, component.height, BufferedImage.TYPE_INT_RGB)
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

    /**
     * Create a component to show the rectangle of the tool window drop target
     *
     * This does not include the highlight for the stripe button, that is handled by the stripe
     */
    internal fun createDropTargetHighlightComponent(): NonOpaquePanel {
      return object: NonOpaquePanel() {
        override fun paint(g: Graphics) {
          g.color = JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND
          g.fillRect(0, 0, width, height)
        }
      }
    }
  }

  override fun canStartDragging(dragComponent: JComponent, dragComponentPoint: Point) =
    getToolWindowAtPoint(RelativePoint(dragComponent, dragComponentPoint)) != null

  override fun processMousePressed(event: MouseEvent) {
    val relativePoint = RelativePoint(event)
    val toolWindow = getToolWindowAtPoint(relativePoint) ?: return
    val component = getComponentFromDragSourcePane(relativePoint)
    val decorator = if (toolWindow.isVisible) toolWindow.decorator else null

    toolWindowRef = WeakReference(toolWindow)
    initialAnchor = toolWindow.anchor

    if (isNewUi) {
      component?.let {
        val parent = it.parent
        if (it.isShowing && parent is AbstractDroppableStripe) {
          lastStripe = parent
          initialButton = it
        }
      }
      floatingWindowSize.size = toolWindow.windowInfo.floatingBounds?.size ?: JBUI.size(500, 400)
    }
    else {
      getSourceStripe(toolWindow.anchor).let {
        lastStripe = it
        initialButton = if (!it.isNewStripes && it.isShowing) it.getButtonFor(toolWindow.id)?.getComponent() else component
        // Size to make the floating toolbar. If the screen point is inside the anchor's drop area, this will be the default size of that
        // drop area. If not, but the tool window is visible and the point is inside the tool window, use the bounds of the tool window
        floatingWindowSize.size = toolWindow.getBoundsOnScreen(toolWindow.anchor, relativePoint.screenPoint).size
      }
    }

    val dragOutImage = if (decorator != null && !decorator.bounds.isEmpty) createThumbnailDragImage(decorator) else null
    val dragImage = initialButton?.let(::createStripeButtonDragImage) ?: dragOutImage
    sourceIsHeader = true

    if (component is StripeButton || component is SquareStripeButton) {
      initialOffset.location = relativePoint.getPoint(component)
      sourceIsHeader = false
    }
    else if (dragImage != null) {
      initialOffset.location = Point(dragImage.getWidth(dragSourcePane) / 4, dragImage.getHeight(dragSourcePane) / 4)
    }

    if (dragImage != null) {
      dragImageDialog = DragImageDialog(dragSourcePane, this, dragImage, dragOutImage)
    }
  }

  override fun getDragStartDeadzone(pressedScreenPoint: Point, draggedScreenPoint: Point): Int {
    val component = getComponentFromDragSourcePane(RelativePoint(pressedScreenPoint))
    if (component is StripeButton || component is SquareStripeButton) {
      return super.getDragStartDeadzone(pressedScreenPoint, draggedScreenPoint)
    }
    return JBUI.scale(Registry.intValue("ide.new.tool.window.start.drag.deadzone", 7, 0, 100))
  }

  override fun isDragOut(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point) = isDragOut(dragToScreenPoint)

  private fun isDragOut(dragToScreenPoint: Point): Boolean {
    if (isNewUi) {
      // TODO: This is the same as lastStripe, which is set to getTargetStripeByDropLocation in relocate
      return getTargetStripeByDropLocation(dragToScreenPoint) == null
    }
    else {
      // It's a drag out operation if the point is not inside any stripe drop area, and also not inside the tool window, if visible
      val toolWindow = getToolWindow()
      if (toolWindow != null && toolWindow.getBoundsOnScreen(toolWindow.anchor, dragToScreenPoint).contains(dragToScreenPoint)) {
        return false
      }

      // If we've got a stripe, we're within its bounds
      return lastStripe == null
    }
  }

  override fun processDragOut(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point, justStarted: Boolean) {
    if (getToolWindow() == null) return
    relocate(event)
    event.consume()
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

  private fun startDrag(event: MouseEvent) {
    relocate(event)
    initialButton?.isVisible = false
    dragImageDialog?.isVisible = true
    with(dragSourcePane.rootPane.glassPane as JComponent) {
      add(dropTargetHighlightComponent)
      revalidate()
      repaint()
    }
    dragSourcePane.buttonManager.startDrag()
  }

  private fun relocate(event: MouseEvent) {
    val screenPoint = event.locationOnScreen
    val dialog = dragImageDialog ?: return
    val toolWindow = getToolWindow() ?: return

    dialog.setLocation(screenPoint.x - initialOffset.x, screenPoint.y - initialOffset.y)
    setDragOut(isDragOut(screenPoint))

    val targetStripe = getTargetStripeByDropLocation(screenPoint)
    lastStripe?.let { if (it != targetStripe) it.resetDrop() }

    dropTargetHighlightComponent.isVisible = targetStripe != null

    if (targetStripe != null && initialButton != null) {
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
      targetStripe.processDropButton(initialButton as JComponent, dialog.contentPane as JComponent, screenPoint)

      if (!isNewUi) {
        SwingUtilities.invokeLater(Runnable {
          if (initialAnchor == null) return@Runnable

          // Get the bounds of the drop target highlight. This will be a default size if the point is inside a stripe's drop area, or the
          // bounds of the tool window if instead it's within the bounds of the tool window (and the tool window is visible)
          val bounds = toolWindow.getBoundsOnScreen(targetStripe.anchor, screenPoint)
          val p = bounds.location
          val dropTargetPane = dragSourcePane
          SwingUtilities.convertPointFromScreen(p, dropTargetPane.rootPane.layeredPane)
          bounds.location = p

          val dropToSide = targetStripe.getDropToSide()
          if (dropToSide != null) {
            val half = if (targetStripe.anchor.isHorizontal) bounds.width / 2 else bounds.height / 2
            if (!targetStripe.anchor.isHorizontal) {
              bounds.height -= half
              if (dropToSide) {
                bounds.y += half
              }
            }
            else {
              bounds.width -= half
              if (dropToSide) {
                bounds.x += half
              }
            }
          }
          dropTargetHighlightComponent.bounds = bounds
        })
      }
    }
    lastStripe = targetStripe
  }

  private fun setDragOut(dragOut: Boolean) {
    dragImageDialog?.setDragOut(dragOut)
    dropTargetHighlightComponent.isVisible = !dragOut
  }

  override fun mouseReleased(e: MouseEvent?) {
    if (getToolWindow() == null) return
    super.mouseReleased(e)
    stopDrag()
  }

  override fun processDragOutFinish(event: MouseEvent) {
    processDragFinish(event, false)
  }

  override fun processDragFinish(event: MouseEvent, willDragOutStart: Boolean) {
    val toolWindow = getToolWindow() ?: return
    if (willDragOutStart) {
      setDragOut(true)
      return
    }

    val dropTargetPane = dragSourcePane

    val screenPoint = event.locationOnScreen
    val preferredStripe = dropTargetPane.buttonManager.getStripeFor(initialAnchor!!)

    // If the drop point is not inside a stripe bounds, but is inside the visible tool window bounds, do nothing - we're not moving
    if (dropTargetPane.getStripeFor(screenPoint, preferredStripe) == null &&
        toolWindow.getBoundsOnScreen(initialAnchor!!, screenPoint).contains(screenPoint)) {
      cancelDragging()
      return
    }

    val stripe = lastStripe
    if (stripe != null) {
      stripe.finishDrop(toolWindow.toolWindowManager)
    }
    else {
      toolWindow.toolWindowManager.setToolWindowType(toolWindow.id, ToolWindowType.FLOATING)
      toolWindow.toolWindowManager.activateToolWindow(toolWindow.id, {
        val w = ComponentUtil.getWindow(toolWindow.component)
        if (w is JDialog) {
          val locationOnScreen = event.locationOnScreen
          if (sourceIsHeader) {
            val decorator = InternalDecoratorImpl.findTopLevelDecorator(toolWindow.component)
            if (decorator != null) {
              val shift = SwingUtilities.convertPoint(decorator, decorator.location, w)
              locationOnScreen.translate(-shift.x, -shift.y)
            }
            locationOnScreen.translate(-initialOffset.x, -initialOffset.y)
          }
          w.location = locationOnScreen
          val bounds = w.bounds
          bounds.size = floatingWindowSize
          ScreenUtil.fitToScreen(bounds)
          w.bounds = bounds
        }
      }, true, null)

      if (isNewUi) {
        val info = toolWindow.toolWindowManager.getLayout().getInfo(toolWindow.id)
        toolWindow.toolWindowManager.setSideToolAndAnchor(id = toolWindow.id,
                                                          anchor = preferredStripe.anchor,
                                                          order = info?.order ?: -1,
                                                          isSplit = false)
      }
    }
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
    getStripeButtonForToolWindow(window)?.isVisible = true
    dragSourcePane.buttonManager.stopDrag()
    with(dragSourcePane.rootPane.glassPane as JComponent) {
      remove(dropTargetHighlightComponent)
      revalidate()
      repaint()
    }

    @Suppress("SSBasedInspection")
    dragImageDialog?.dispose()
    dragImageDialog = null
    toolWindowRef = null
    initialAnchor = null
    lastStripe?.resetDrop()
    lastStripe = null
    initialButton = null
  }

  private fun getToolWindow() = toolWindowRef?.get()

  private fun getComponentFromDragSourcePane(point: RelativePoint) : Component? =
    point.getPoint(dragSourcePane).let { SwingUtilities.getDeepestComponentAt(dragSourcePane, it.x, it.y) } ?:
    point.getPoint(dragSourcePane.parent).let { SwingUtilities.getDeepestComponentAt(dragSourcePane.parent, it.x, it.y) }

  private fun getToolWindowAtPoint(point: RelativePoint): ToolWindowImpl? {
    val clickedComponent = getComponentFromDragSourcePane(point)
    if (clickedComponent != null && isComponentDraggable(clickedComponent)) {
      val decorator = InternalDecoratorImpl.findNearestDecorator(clickedComponent)
      if (decorator != null &&
          (decorator.toolWindow.anchor != BOTTOM ||
           decorator.locationOnScreen.y < point.screenPoint.y - ToolWindowPane.headerResizeArea))
        return decorator.toolWindow
    }

    return when (clickedComponent) {
      is StripeButton -> clickedComponent.toolWindow
      is SquareStripeButton -> clickedComponent.toolWindow
      else -> null
    }
  }

  // When the point is inside an anchor's drop area, returns a default size for the resulting tool window or floating window. If it's not
  // inside an anchor's drop area, and the tool window is visible, and the point is inside the tool window's bounds, return those bounds
  // TODO: Split up this method - it's hard to reason about
  // Can simplify if we split the check for anchor bounds from the subsequent visible tool window bounds check
  private fun ToolWindowImpl.getBoundsOnScreen(anchor: ToolWindowAnchor, screenPoint: Point): Rectangle {
    if (isNewUi) return Rectangle()

    // Semantically, we don't know if we're being asked for the current tool window bounds (e.g. to set the size of the floating window, or
    // to see if it's a drag out operation) or for the bounds of the drop target for highlighting, etc.
    // Another reason to split this into more explicit methods
    val pane = dragSourcePane

    // Remember that the pane covers the entire frame, not just the bounds of the tool window
    val location = pane.locationOnScreen
    var width = pane.width
    var height = pane.height

    // Get the bounds of the content area of the pane, i.e. minus the bounds of the stripes
    location.x += getStripeWidth(pane, LEFT)
    location.y += getStripeHeight(pane, TOP)
    width -= getStripeWidth(pane, LEFT) + getStripeWidth(pane, RIGHT)
    height -= getStripeHeight(pane, TOP) + getStripeHeight(pane, BOTTOM)

    // Try to find the stripe for the point, preferring the tool window's current stripe to avoid overlaps
    val targetStripe = pane.getStripeFor(screenPoint, pane.buttonManager.getStripeFor(initialAnchor!!))

    if (anchor === initialAnchor && targetStripe == null && isVisible) {
      if (anchor.isHorizontal) height = (pane.rootPane.height * windowInfo.weight).toInt()
      else width = (pane.rootPane.width * windowInfo.weight).toInt()
    }
    else {
      if (anchor.isHorizontal) height = AbstractDroppableStripe.DROP_DISTANCE_SENSITIVITY
      else width = AbstractDroppableStripe.DROP_DISTANCE_SENSITIVITY
    }

    when (anchor) {
      BOTTOM -> {
        location.y = pane.locationOnScreen.y + pane.height - height - getStripeHeight(pane, BOTTOM)
      }
      RIGHT -> {
        location.x = pane.locationOnScreen.x + pane.width - width - getStripeWidth(pane, RIGHT)
      }
    }
    return Rectangle(location.x, location.y, width, height)
  }

  private fun getStripeWidth(pane: ToolWindowPane, anchor: ToolWindowAnchor): Int {
    val stripe = pane.buttonManager.getStripeFor(anchor)
    return if (stripe.isVisible && stripe.isShowing) stripe.width else 0
  }

  private fun getStripeHeight(pane: ToolWindowPane, anchor: ToolWindowAnchor): Int {
    val stripe = pane.buttonManager.getStripeFor(anchor)
    return if (stripe.isVisible && stripe.isShowing) stripe.height else 0
  }

  private fun getStripeButtonForToolWindow(window: ToolWindowImpl?): JComponent? {
    return window?.let { dragSourcePane.buttonManager.getStripeFor(it.anchor).getButtonFor(it.id)?.getComponent() }
  }

  private fun getSourceStripe(anchor: ToolWindowAnchor) = dragSourcePane.buttonManager.getStripeFor(anchor)

  /**
   * Finds the stripe whose drop area contains the screen point. Prioritises the initial anchor to avoid overlaps
   *
   * The drop area of a stripe is implementation defined, and might be just the stripe, or the stripe plus extended bounds
   */
  private fun getTargetStripeByDropLocation(screenPoint: Point): AbstractDroppableStripe? {
    val preferred = getSourceStripe(initialAnchor!!)
    val dropTargetPane = dragSourcePane
    // Get the stripe whose (possibly extended) bounds contains the screen point. If we can't find a stripe, check if the point is inside
    // the visible bounds of the tool window, in which case use the tool window's anchor
    val stripe = dropTargetPane.getStripeFor(screenPoint, preferred)
                 ?: if (getToolWindow()!!.getBoundsOnScreen(initialAnchor!!, screenPoint).contains(screenPoint)) preferred else null

    // TODO: If we want to get rid of the top stripe, we should remove it in the button managers
    return if (stripe?.anchor == TOP) null else stripe
  }

  /**
   * Create a drag image for the given component, which is expected to be a stripe button
   */
  private fun createStripeButtonDragImage(component: Component): BufferedImage? {
    val initialBounds = component.bounds
    try {
      if (initialBounds.isEmpty) {
        component.size = component.preferredSize
      }

      val areaSize = when (component) {
        is StripeButton -> component.size.also {
          val delta = JBUIScale.scale(1)
          it.width -= delta
          it.height -= delta
        }
        is SquareStripeButton -> component.size.also {
          JBInsets.removeFrom(it, component.insets)
          JBInsets.removeFrom(it, SquareStripeButtonLook.ICON_PADDING)
        }
        else -> JBUI.emptySize()
      }

      if (!areaSize.isNotEmpty()) {
        return null
      }

      val image = ImageUtil.createImage(component.graphicsConfiguration, areaSize.width, areaSize.height, BufferedImage.TYPE_INT_RGB)
      image.graphics.let {
        it.color = if (isNewUi) {
          JBUI.CurrentTheme.ToolWindow.DragAndDrop.BUTTON_FLOATING_BACKGROUND
        }
        else {
          UIUtil.getBgFillColor(component.parent)
        }

        it.fillRect(0, 0, areaSize.width, areaSize.height)

        when (component) {
          is StripeButton -> component.paint(it)
          is SquareStripeButton -> component.paintDraggingButton(it)
        }

        it.dispose()
      }
      return image
    }
    finally {
      component.bounds = initialBounds
    }
  }

  private class DragImageDialog(owner: JComponent,
                                @JvmField val helper: ToolWindowDragHelper,
                                private val stripeButtonImage: BufferedImage,
                                private val toolWindowThumbnailImage: BufferedImage?)
    : JDialog(ComponentUtil.getWindow(owner), null, ModalityType.MODELESS) {

    private var dragOut: Boolean? = null

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
        override fun mouseReleased(e: MouseEvent) {
          // stop drag
          helper.mouseReleased(e)
        }

        override fun mouseDragged(e: MouseEvent) {
          helper.relocate(e)
        }
      })
      setDragOut(false)
    }

    fun setDragOut(dragOut: Boolean) {
      if (dragOut == this.dragOut) return
      this.dragOut = dragOut
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