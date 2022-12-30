// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowAnchor.*
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.impl.*
import com.intellij.openapi.wm.safeToolWindowPaneId
import com.intellij.ui.ComponentUtil
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.ScreenUtil
import com.intellij.ui.awt.DevicePoint
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.paint.RectanglePainter
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
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
  private var initialIsSplit: Boolean? = null
  private var initialStripeButton: StripeButtonManager? = null
  private val initialOffset = Point()
  private val floatingWindowSize = Dimension()
  private var lastStripe: AbstractDroppableStripe? = null
  private var lastDropTargetPaneId: String? = null
  private var lastDropTargetPane: ToolWindowPane? = null
  private var dragImageDialog: DragImageDialog? = null

  companion object {
    const val THUMB_OPACITY = .85f

    /**
     * Create a potentially scaled image of the component to use as a drag image
     */
    internal fun createThumbnailDragImage(component: JComponent, thumbSize: Int = JBUI.scale(THUMB_SIZE)): BufferedImage {
      val image = ImageUtil.createImage(component.graphicsConfiguration, component.width, component.height, BufferedImage.TYPE_INT_RGB)
      val graphics = image.graphics
      graphics.color = UIUtil.getBgFillColor(component)
      RectanglePainter.FILL.paint(graphics as Graphics2D, 0, 0, component.width, component.height, null)
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
    val toolWindow = getToolWindowAtPoint(RelativePoint(event)) ?: return
    toolWindowRef = WeakReference(toolWindow)
  }

  override fun getDragStartDeadzone(pressedScreenPoint: Point, draggedScreenPoint: Point): Int {
    // The points are screen points from the event, which is in the same coordinate system as the dragSourcePane
    val point = pressedScreenPoint.location.also { SwingUtilities.convertPointFromScreen(it, dragSourcePane) }
    val component = getComponentFromDragSourcePane(RelativePoint(dragSourcePane, point))
    if (component is StripeButton || component is SquareStripeButton) {
      return super.getDragStartDeadzone(pressedScreenPoint, draggedScreenPoint)
    }
    return JBUI.scale(Registry.intValue("ide.new.tool.window.start.drag.deadzone", 7, 0, 100))
  }

  override fun isDragOut(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point) =
    isDragOut(DevicePoint(event))

  private fun isDragOut(devicePoint: DevicePoint): Boolean {
    if (!isNewUi && isPointInVisibleDockedToolWindow(devicePoint)) {
      return false
    }

    // If we've got a stripe, we're within its bounds
    // Note that this is a shortcut for getTargetStripeByDropLocation(devicePoint, preferredStripe) et al
    // Make sure lastStripe is up-to-date before calling isDragOut!
    return lastStripe == null
  }

  override fun processDragOut(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point, dragOutJustStarted: Boolean) {
    if (getToolWindow() == null) return
    if (isDragJustStarted) {
      startDrag(event, startScreenPoint)
    }
    if (dragOutJustStarted) {
      setDragOut(true)
    }
    relocate(event)
    event.consume()
  }

  override fun processDrag(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point) {
    if (!checkModifiers(event)) return
    if (isDragJustStarted) {
      startDrag(event, startScreenPoint)
    }
    else {
      relocate(event)
    }
  }

  private fun startDrag(event: MouseEvent, startScreenPoint: Point) {
    val startPoint = Point(startScreenPoint).also { SwingUtilities.convertPointFromScreen(it, event.component) }
    val relativePoint = RelativePoint(event.component, startPoint)
    val toolWindow = getToolWindow() ?: return
    val clickedComponent = getComponentFromDragSourcePane(relativePoint)
    val decorator = if (toolWindow.isVisible) toolWindow.decorator else null

    initialAnchor = toolWindow.anchor
    initialIsSplit = toolWindow.isSplitMode

    getSourceStripe(toolWindow.anchor, toolWindow.isSplitMode).let {
      lastStripe = it
      // The returned button might not be showing if stripes are hidden. This is not supported for new UI
      initialStripeButton = if (it.isShowing || !it.isNewStripes) it.getButtonFor(toolWindow.id) else null
      floatingWindowSize.size = toolWindow.windowInfo.floatingBounds?.size ?: getDefaultFloatingToolWindowSize(toolWindow)
    }

    val dragImageComponent = initialStripeButton?.getComponent() ?: clickedComponent
    val dragOutImage = if (decorator != null && !decorator.bounds.isEmpty) createThumbnailDragImage(decorator) else null
    val dragImage = dragImageComponent?.let(::createStripeButtonDragImage) ?: dragOutImage

    if (clickedComponent is StripeButton || clickedComponent is SquareStripeButton) {
      initialOffset.location = relativePoint.getPoint(clickedComponent).also {
        if (clickedComponent is SquareStripeButton) {
          it.x -= clickedComponent.insets.left + SquareStripeButtonLook.ICON_PADDING.left
          it.y -= clickedComponent.insets.top + SquareStripeButtonLook.ICON_PADDING.top
        }
      }
    }
    else if (dragImage != null) {
      initialOffset.location = Point(dragImage.getWidth(dragSourcePane) / 4, dragImage.getHeight(dragSourcePane) / 4)
    }

    if (dragImage != null) {
      dragImageDialog = DragImageDialog(dragSourcePane, this, dragImage, dragOutImage)
    }

    relocate(event)
    initialStripeButton?.getComponent()?.isVisible = false
    dragImageDialog?.isVisible = true
    addDropTargetHighlighter(dragSourcePane)
    dragSourcePane.buttonManager.startDrag()
  }

  private fun relocate(event: MouseEvent) {
    val eventDevicePoint = DevicePoint(event)
    val dialog = dragImageDialog ?: return
    val toolWindow = getToolWindow() ?: return

    val originalDialogSize = dialog.size

    // Initial offset is relative to the original component and therefore in screen coordinates. The dialog size is a pixel size, and is the
    // same in all coordinates - it's not scaled between screens. This means it has the same size relative to the UI, but not the same
    // physical size based on accurate DPI settings
    val dialogScreenLocation = eventDevicePoint.locationOnScreen.also {
      it.translate(-initialOffset.x, -initialOffset.y)
    }
    val newDialogScreenBounds = Rectangle(dialogScreenLocation, originalDialogSize)

    dialog.bounds = newDialogScreenBounds

    // Verify that the bounds are still correct. When moving the dialog across two screens with different DPI scale factors in Windows, the
    // dialog might get resized or relocated, which is at best undesirable, and at worst incorrect (and possibly a bug in the JBR).
    // When the dialog gets more than half of its width into the next screen, Windows considers it to belong to the next screen (although
    // it doesn't yet update the graphicsConfiguration property), and resizes it. It tries to maintain the same physical size, based on DPI.
    // A screen with a 100% scaling factor will be 96 DPI, but a 150% screen will have a DPI of 144. Moving from a 150% screen to a 100%
    // screen will convert a size of 144 to 96, meaning the dialog will shrink. Moving in the opposite direction will cause the dialog to
    // grow.
    // Unfortunately, resizing the dialog will also change the width and move the dialog back to the original screen, but the size is not
    // reset. Continuing the drag will soon put half of the dialog back into the next screen, and the dialog is resized again. This
    // continues until the dialog is tiny. Going in the opposite direction will cause the dialog to repeatedly grow huge.
    // Fortunately, we want the drag image to be the same size relative to the UI, so it's the same "pixel" size regardless of DPI. We can
    // simply reset the size to the same value, and we avoid any problems. There is still a visual step as the DPI changes - the pixel
    // values are the same, but the DPIs are different. This is normal behaviour for Windows, and can be seen with e.g. Notepad.
    // Windows will also sometimes relocate the dialog, but this appears to be incorrect behaviour, possibly a bug in the JBR. After moving
    // halfway into the next screen, the dialog can sometimes (and reproducibly) relocate to an incorrect location, as though the
    // calculation to convert from device to screen coordinates is incredibly wrong (e.g. a 2880x1800@150% screen should be positioned at
    // x=1842 based on screen coordinates of the first screen, or x=2763 based on screen coordinates of the second screen, but instead is
    // shown at x=3919). Continue dragging, and it bounces between the correct location and similar incorrect locations until the dialog is
    // approximately 3/4 of the way into the next screen. Perhaps this is related to the graphicsConfiguration property not being updated
    // correctly. Is the JBR confused about what scaling factors to apply?
    // TODO: Investigate why the JBR is positioning the dialog like this
    if (dialog.bounds != newDialogScreenBounds) {
      dialog.size = originalDialogSize
    }

    val preferredStripe = getSourceStripe(toolWindow.anchor, toolWindow.isSplitMode)
    val targetStripe = getTargetStripeByDropLocation(eventDevicePoint, preferredStripe)
                       ?: if (!isNewUi && isPointInVisibleDockedToolWindow(eventDevicePoint)) preferredStripe else null
    lastStripe?.let {
      if (it != targetStripe) {
        removeDropTargetHighlighter(toolWindow.toolWindowManager.getToolWindowPane(it.paneId))
        it.resetDrop()
      }
    }

    // Make sure to set lastStripe before calling isDragOut
    lastStripe = targetStripe
    setDragOut(isDragOut(eventDevicePoint))

    val initialStripeButton = this.initialStripeButton
    if (targetStripe != null && initialStripeButton != null) {
      addDropTargetHighlighter(toolWindow.toolWindowManager.getToolWindowPane(targetStripe.paneId))

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
      targetStripe.processDropButton(initialStripeButton, dialog.contentPane as JComponent, eventDevicePoint)

      if (lastDropTargetPaneId != targetStripe.paneId) {
        lastDropTargetPaneId = targetStripe.paneId
        lastDropTargetPane = toolWindow.toolWindowManager.getToolWindowPane(targetStripe.paneId)
      }

      if (!isNewUi) {
        SwingUtilities.invokeLater(Runnable {
          if (initialAnchor == null || initialIsSplit == null) return@Runnable

          // Get the bounds of the drop target highlight. If the point is inside the drop target bounds, use those bounds. If it's not, but
          // it's inside the bounds of the tool window (and the tool window is visible), then use the tool window bounds. Note that when
          // docked, the tool window's screen coordinate system will be the same as the mouse event's. But if it's floating, it might be on
          // another screen (although if it's floating, we don't get bounds)
          val toolWindowBounds = getToolWindowScreenBoundsIfVisibleAndDocked(toolWindow)?.takeIf {
            getTargetStripeByDropLocation(eventDevicePoint, preferredStripe) == null && it.contains(event.locationOnScreen)
          }
          val bounds = toolWindowBounds ?: getDropTargetScreenBounds(lastDropTargetPane!!, targetStripe.anchor)

          bounds.location = bounds.location.also { SwingUtilities.convertPointFromScreen(it, lastDropTargetPane!!.rootPane.layeredPane) }

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
  }

  private fun setDragOut(dragOut: Boolean) {
    dragImageDialog?.setDragOut(dragOut)
    dropTargetHighlightComponent.isVisible = !dragOut
  }

  override fun processDragOutFinish(event: MouseEvent) = processDragFinish(event, false)

  override fun processDragFinish(event: MouseEvent, willDragOutStart: Boolean) {
    val toolWindow = getToolWindow() ?: return
    if (willDragOutStart) {
      return
    }

    try {
      val devicePoint = DevicePoint(event)
      val preferredStripe = getSourceStripe(initialAnchor!!, initialIsSplit!!)

      // If the drop point is not inside a stripe bounds, but is inside the visible tool window bounds, do nothing - we're not moving.
      // Note that we must check the stripe because we might be moving from top to bottom or left to right
      if (!isNewUi && getTargetStripeByDropLocation(devicePoint, preferredStripe) == null && isPointInVisibleDockedToolWindow(devicePoint)) {
        return
      }

      val stripe = lastStripe
      if (stripe != null) {
        stripe.finishDrop(toolWindow.toolWindowManager)
      }
      else {
        // Set the bounds before we show the window, to avoid a visible jump
        toolWindow.applyWindowInfo(toolWindow.toolWindowManager.getRegisteredMutableInfoOrLogError(toolWindow.id).also {
          val bounds = Rectangle(devicePoint.locationOnScreen, floatingWindowSize)
          bounds.translate(-initialOffset.x, -initialOffset.y)
          ScreenUtil.fitToScreen(bounds)
          it.floatingBounds = bounds
        })

        toolWindow.toolWindowManager.setToolWindowType(toolWindow.id, ToolWindowType.FLOATING)
        toolWindow.toolWindowManager.activateToolWindow(toolWindow.id, null, true, null)

        if (isNewUi) {
          val info = toolWindow.toolWindowManager.getLayout().getInfo(toolWindow.id)
          toolWindow.toolWindowManager.setSideToolAndAnchor(id = toolWindow.id,
                                                            paneId = info?.safeToolWindowPaneId ?: WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID,
                                                            anchor = preferredStripe.anchor,
                                                            order = info?.order ?: -1,
                                                            isSplit = preferredStripe.split)
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
    lastDropTargetPane?.let { removeDropTargetHighlighter(it) }
    dropTargetHighlightComponent.bounds = Rectangle(0, 0, 0, 0)

    @Suppress("SSBasedInspection")
    dragImageDialog?.dispose()
    dragImageDialog = null
    toolWindowRef = null
    initialAnchor = null
    initialIsSplit = null
    lastStripe?.resetDrop()
    lastStripe = null
    lastDropTargetPaneId = null
    lastDropTargetPane = null
    initialStripeButton = null
  }

  private fun addDropTargetHighlighter(pane: ToolWindowPane) {
    with(pane.rootPane.glassPane as JComponent) {
      if (!isAncestorOf(dropTargetHighlightComponent)) {
        add(dropTargetHighlightComponent)
        revalidate()
        repaint()
      }
    }
  }

  private fun removeDropTargetHighlighter(pane: ToolWindowPane) {
    with(pane.rootPane.glassPane as JComponent) {
      if (isAncestorOf(dropTargetHighlightComponent)) {
        remove(dropTargetHighlightComponent)
        revalidate()
        repaint()
      }
    }
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


  private fun getToolWindowScreenBoundsIfVisibleAndDocked(toolWindow: ToolWindowImpl): Rectangle? {
    if (!toolWindow.isVisible || toolWindow.type == ToolWindowType.FLOATING || toolWindow.type == ToolWindowType.WINDOWED) return null

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
  private fun getDefaultFloatingToolWindowSize(toolWindow: ToolWindowImpl): Dimension {
    return getAdjustedPaneContentsScreenBounds(dragSourcePane, toolWindow.anchor,
                                               (dragSourcePane.rootPane.height * toolWindow.windowInfo.weight).toInt(),
                                               (dragSourcePane.rootPane.width * toolWindow.windowInfo.weight).toInt()).size
  }

  /**
   * Gets the screen bounds for the drop area of the given anchor
   */
  private fun getDropTargetScreenBounds(dropTargetPane: ToolWindowPane, anchor: ToolWindowAnchor) =
    getAdjustedPaneContentsScreenBounds(dropTargetPane, anchor, AbstractDroppableStripe.DROP_DISTANCE_SENSITIVITY, AbstractDroppableStripe.DROP_DISTANCE_SENSITIVITY)

  private fun isPointInVisibleDockedToolWindow(devicePoint: DevicePoint) =
    getToolWindow()?.let { getToolWindowScreenBoundsIfVisibleAndDocked(it)?.contains(devicePoint.getLocationOnScreen(it.component)) } ?: false

  private fun getStripeWidth(pane: ToolWindowPane, anchor: ToolWindowAnchor) = pane.buttonManager.getStripeWidth(anchor)
  private fun getStripeHeight(pane: ToolWindowPane, anchor: ToolWindowAnchor) = pane.buttonManager.getStripeHeight(anchor)

  private fun getStripeButtonForToolWindow(window: ToolWindowImpl?): JComponent? {
    return window?.let { dragSourcePane.buttonManager.getStripeFor(it.anchor, it.isSplitMode).getButtonFor(it.id)?.getComponent() }
  }

  private fun getSourceStripe(anchor: ToolWindowAnchor, isSplit: Boolean) = dragSourcePane.buttonManager.getStripeFor(anchor, isSplit)

  /**
   * Finds the stripe whose drop area contains the screen point. Prioritises the initial anchor to avoid overlaps
   *
   * The drop area of a stripe is implementation defined, and might be just the stripe, or the stripe plus extended bounds
   */
  private fun getTargetStripeByDropLocation(devicePoint: DevicePoint, preferredStripe: AbstractDroppableStripe): AbstractDroppableStripe? {
    fun getTargetStripeForOtherPanes(devicePoint: DevicePoint, preferredStripe: AbstractDroppableStripe): AbstractDroppableStripe? {
      getToolWindow()?.toolWindowManager?.getToolWindowPanes()?.forEach { pane ->
        if (pane != dragSourcePane) {
          pane.buttonManager.getStripeFor(devicePoint, preferredStripe, pane)?.let { return it }
        }
      }
      return null
    }

    val stripe = dragSourcePane.getStripeFor(devicePoint, preferredStripe)
                 ?: getTargetStripeForOtherPanes(devicePoint, preferredStripe)
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
      type = Type.POPUP
      focusableWindowState = false
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