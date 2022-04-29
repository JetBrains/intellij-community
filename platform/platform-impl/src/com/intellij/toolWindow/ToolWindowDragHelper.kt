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
        floatingWindowSize.size = toolWindow.windowInfo.floatingBounds?.size
                                  ?: getDefaultFloatingToolWindowBounds(toolWindow).size
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
    if (!isNewUi && isPointInVisibleToolWindow(dragToScreenPoint)) {
      return false
    }

    // If we've got a stripe, we're within its bounds
    return lastStripe == null
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

    val preferredStripe = getSourceStripe(toolWindow.anchor)
    val targetStripe = getTargetStripeByDropLocation(screenPoint, preferredStripe)
                       ?: if (!isNewUi && isPointInVisibleToolWindow(screenPoint)) preferredStripe else null
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

          val dropTargetPane = dragSourcePane

          // Get the bounds of the drop target highlight. If the point is inside the drop target bounds, use those bounds. If it's not, but
          // it's inside the bounds of the tool window (and the tool window is visible), then use the tool window bounds
          val toolWindowBounds = getToolWindowScreenBoundsIfVisible(toolWindow)?.takeIf {
            getTargetStripeByDropLocation(screenPoint, preferredStripe) == null && it.contains(screenPoint)
          }
          val bounds = toolWindowBounds ?: getDropTargetScreenBounds(dropTargetPane, targetStripe.anchor)

          bounds.location = bounds.location.also { SwingUtilities.convertPointFromScreen(it, dropTargetPane.rootPane.layeredPane) }

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

  override fun processDragOutFinish(event: MouseEvent) = processDragFinish(event, false)

  override fun processDragFinish(event: MouseEvent, willDragOutStart: Boolean) {
    val toolWindow = getToolWindow() ?: return
    if (willDragOutStart) {
      setDragOut(true)
      return
    }

    try {
      val dropTargetPane = dragSourcePane

      val screenPoint = event.locationOnScreen
      val preferredStripe = dropTargetPane.buttonManager.getStripeFor(initialAnchor!!)

      // If the drop point is not inside a stripe bounds, but is inside the visible tool window bounds, do nothing - we're not moving.
      // Note that we must check the stripe because we might be moving from top to bottom or left to right
      if (!isNewUi && dropTargetPane.getStripeFor(screenPoint, preferredStripe) == null && isPointInVisibleToolWindow(screenPoint)) {
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
    finally {
      stopDrag()
    }
  }

  override fun processDragOutCancel() = stopDrag()
  override fun processDragCancel() = stopDrag()

  override fun stop() {
    super.stop()
    // Would stop ever be called in the middle of a drag? This implies the project is disposed mid-drag
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

  private fun getPaneContentScreenBounds(pane: ToolWindowPane): Rectangle {
    val location = pane.locationOnScreen
    location.x += getStripeWidth(pane, LEFT)
    location.y += getStripeHeight(pane, TOP)
    val width = pane.width - getStripeWidth(pane, LEFT) - getStripeWidth(pane, RIGHT)
    val height = pane.height - getStripeHeight(pane, TOP) - getStripeHeight(pane, BOTTOM)
    return Rectangle(location.x, location.y, width, height)
  }

  /**
   * Gets the screen bounds of the pane, minus stripes. Adjusts the height for horizontal tool windows and the width for vertical
   */
  private fun getAdjustedPaneContentsScreenBounds(pane: ToolWindowPane,
                                                  anchor: ToolWindowAnchor,
                                                  adjustedHorizontalHeight: Int,
                                                  adjustedVerticalWidth: Int): Rectangle {
    return getPaneContentScreenBounds(pane).also {
      val paneHeight = it.height
      val paneWidth = it.width

      // Note that this doesn't modify width/height for splits
      if (anchor.isHorizontal) {
        it.height = adjustedHorizontalHeight
      }
      else {
        it.width = adjustedVerticalWidth
      }

      when (anchor) {
        BOTTOM -> it.y = it.y + paneHeight - it.height
        RIGHT -> it.x = it.x + paneWidth - it.width
      }

      // TODO: Adjust for half height/width tool windows?
      // This means that drop target highlight, drag out boundaries and default floating size use the full height/width for split tool
      // windows. The same for the bounds used to ignore a drop over a tool window
      // Would this be the right place for that?
    }
  }


  private fun getToolWindowScreenBoundsIfVisible(toolWindow: ToolWindowImpl): Rectangle? {
    if (!toolWindow.isVisible) return null

    // We can't just use toolWindow.component.bounds, as this doesn't include headers, etc.
    return getAdjustedPaneContentsScreenBounds(dragSourcePane, toolWindow.anchor,
                                               (dragSourcePane.rootPane.height * toolWindow.windowInfo.weight).toInt(),
                                               (dragSourcePane.rootPane.width * toolWindow.windowInfo.weight).toInt())
  }

  /**
   * Calculates the default size for the tool window if the visible bounds are not available.
   *
   * The default size is the same as its drop target size as measured on the source pane (the tool window always belongs to the source pane)
   */
  private fun getDefaultFloatingToolWindowBounds(toolWindow: ToolWindowImpl): Rectangle {
    return getAdjustedPaneContentsScreenBounds(dragSourcePane, toolWindow.anchor,
                                               (dragSourcePane.rootPane.height * toolWindow.windowInfo.weight).toInt(),
                                               (dragSourcePane.rootPane.width * toolWindow.windowInfo.weight).toInt())
  }

  /**
   * Gets the screen bounds for the drop area of the given anchor
   */
  private fun getDropTargetScreenBounds(dropTargetPane: ToolWindowPane, anchor: ToolWindowAnchor) =
    getAdjustedPaneContentsScreenBounds(dropTargetPane, anchor, AbstractDroppableStripe.DROP_DISTANCE_SENSITIVITY, AbstractDroppableStripe.DROP_DISTANCE_SENSITIVITY)

  private fun isPointInVisibleToolWindow(dragToScreenPoint: Point) =
    getToolWindow()?.let { getToolWindowScreenBoundsIfVisible(it)?.contains(dragToScreenPoint) } ?: false

  private fun getStripeWidth(pane: ToolWindowPane, anchor: ToolWindowAnchor) = pane.buttonManager.getStripeWidth(anchor)
  private fun getStripeHeight(pane: ToolWindowPane, anchor: ToolWindowAnchor) = pane.buttonManager.getStripeHeight(anchor)

  private fun getStripeButtonForToolWindow(window: ToolWindowImpl?): JComponent? {
    return window?.let { dragSourcePane.buttonManager.getStripeFor(it.anchor).getButtonFor(it.id)?.getComponent() }
  }

  private fun getSourceStripe(anchor: ToolWindowAnchor) = dragSourcePane.buttonManager.getStripeFor(anchor)

  /**
   * Finds the stripe whose drop area contains the screen point. Prioritises the initial anchor to avoid overlaps
   *
   * The drop area of a stripe is implementation defined, and might be just the stripe, or the stripe plus extended bounds
   */
  private fun getTargetStripeByDropLocation(screenPoint: Point, preferredStripe: AbstractDroppableStripe): AbstractDroppableStripe? {
    val stripe = dragSourcePane.getStripeFor(screenPoint, preferredStripe)
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