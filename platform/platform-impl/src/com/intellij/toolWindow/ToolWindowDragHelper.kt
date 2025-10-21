// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.ide.HelpTooltip
import com.intellij.ide.actions.ToolWindowMoveAction
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ToolWindowAnchor.*
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.impl.*
import com.intellij.ui.*
import com.intellij.ui.awt.DevicePoint
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.drag.DialogDragImageView
import com.intellij.ui.drag.DialogWithImage
import com.intellij.ui.drag.DragImageView
import com.intellij.ui.drag.GlassPaneDragImageView
import com.intellij.ui.paint.RectanglePainter
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.lang.ref.WeakReference
import javax.swing.*

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
  private var dragSession: DragSession? = null
  private var dragMoreButton: MoreSquareStripeButton? = null
  private var dragMoreButtonNewSide: ToolWindowAnchor? = null

  private var lastDropTooltipAnchor: ToolWindowMoveAction.Anchor? = null
  private var dropTooltipPopup: JBPopup? = null

  companion object {
    const val THUMB_OPACITY: Float = .85f

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
          if (ExperimentalUI.isNewUI()) {
            g.color = JBUI.CurrentTheme.ToolWindow.DragAndDrop.AREA_BACKGROUND
          }
          else {
            g.color = JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND
          }
          g.fillRect(0, 0, width, height)
        }
      }
    }
  }

  override fun canStartDragging(dragComponent: JComponent, dragComponentPoint: Point): Boolean {
    val point = RelativePoint(dragComponent, dragComponentPoint)
    return getToolWindowAtPoint(point) != null || getComponentFromDragSourcePane(point) is MoreSquareStripeButton
  }

  override fun processMousePressed(event: MouseEvent) {
    val toolWindow = getToolWindowAtPoint(RelativePoint(event)) ?: return
    toolWindowRef = WeakReference(toolWindow)
  }

  override fun getDragStartDeadzone(pressedScreenPoint: Point, draggedScreenPoint: Point): Int {
    // The points are screen points from the event, which is in the same coordinate system as the dragSourcePane
    val point = pressedScreenPoint.location.also { SwingUtilities.convertPointFromScreen(it, dragSourcePane) }
    val component = getComponentFromDragSourcePane(RelativePoint(dragSourcePane, point))
    if (component is StripeButton || component is AbstractSquareStripeButton) {
      return super.getDragStartDeadzone(pressedScreenPoint, draggedScreenPoint)
    }
    return JBUI.scale(Registry.intValue("ide.new.tool.window.start.drag.deadzone", 7, 0, 100))
  }

  override fun isDragOut(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point): Boolean {
    if (getToolWindow() == null) {
      val dragComponentPoint = Point(startScreenPoint)
      SwingUtilities.convertPointFromScreen(dragComponentPoint, myDragComponent)
      val clickedComponent = getComponentFromDragSourcePane(RelativePoint(myDragComponent, dragComponentPoint))
      if (clickedComponent is MoreSquareStripeButton) {
        return false
      }
    }
    return isDragOut(DevicePoint(event))
  }

  private fun isDragOut(devicePoint: DevicePoint): Boolean {
    if (isPointInVisibleDockedToolWindow(devicePoint)) {
      return false
    }

    // If we've got a stripe, we're within its bounds
    // Note that this is a shortcut for getTargetStripeByDropLocation(devicePoint, preferredStripe) et al
    // Make sure lastStripe is up-to-date before calling isDragOut!
    return lastStripe == null
  }

  override fun processDragOut(event: MouseEvent, dragToScreenPoint: Point, startScreenPoint: Point, dragOutJustStarted: Boolean) {
    if (getToolWindow() == null || !checkModifiers(event)) return
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
    val toolWindow = getToolWindow()
    val clickedComponent = getComponentFromDragSourcePane(relativePoint)

    if (toolWindow == null) {
      if (clickedComponent is MoreSquareStripeButton) {
        dragMoreButton = clickedComponent
        val dragImage = createStripeButtonDragImage(clickedComponent)
        if (dragImage != null) {
          dragSession = DragSession(
            view = createDragImageView(event),
            stripeButtonImage = dragImage,
            toolWindowThumbnailImage = null,
          )
          dragSession!!.start()
        }
        setInitialOffsetFromStripeButton(relativePoint, clickedComponent)
        relocate(event)
        addDropTargetHighlighter(dragSourcePane)
        dropTargetHighlightComponent.isVisible = true
        clickedComponent.setDragState(true)
      }
      return
    }

    overlayStripesIfHidden(toolWindow, true)

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
      setInitialOffsetFromStripeButton(relativePoint, clickedComponent)
    }
    else if (dragImage != null) {
      initialOffset.location = Point(dragImage.getWidth(dragSourcePane) / 4, dragImage.getHeight(dragSourcePane) / 4)
    }

    if (dragImage != null) {
      dragSession = DragSession(
        view = createDragImageView(event),
        stripeButtonImage = dragImage,
        toolWindowThumbnailImage = dragOutImage
      )
    }

    relocate(event)
    initialStripeButton?.getComponent()?.isVisible = false
    dragSession?.start()
    addDropTargetHighlighter(dragSourcePane)
    dragSourcePane.buttonManager.startDrag()
  }

  private fun createDragImageView(event: MouseEvent): DragImageView = if (StartupUiUtil.isWaylandToolkit()) {
    GlassPaneDragImageView(IdeGlassPaneUtil.find(event.component))
  }
  else {
    DialogDragImageView(DragImageDialog(dragSourcePane, this))
  }

  private fun setInitialOffsetFromStripeButton(relativePoint: RelativePoint, clickedComponent: Component) {
    initialOffset.location = relativePoint.getPoint(clickedComponent).also {
      if (clickedComponent is AbstractSquareStripeButton) {
        it.x -= clickedComponent.insets.left + SquareStripeButtonLook.getIconPadding(clickedComponent.isOnTheLeftStripe()).left
        it.y -= clickedComponent.insets.top + SquareStripeButtonLook.getIconPadding(clickedComponent.isOnTheLeftStripe()).top
      }
    }
  }

  private fun overlayStripesIfHidden(toolWindow: ToolWindowImpl, show: Boolean) {
    if (UISettings.getInstance().hideToolStripes) {
      for (pane in toolWindow.toolWindowManager.getToolWindowPanes()) {
        pane.setStripesOverlaid(show)
      }
    }
  }

  private fun relocateImageView(event: MouseEvent) {
    val view = dragSession?.view ?: return

    val eventDevicePoint = DevicePoint(event)

    // Initial offset is relative to the original component and therefore in screen coordinates. The dialog size is a pixel size, and is the
    // same in all coordinates - it's not scaled between screens. This means it has the same size relative to the UI, but not the same
    // physical size based on accurate DPI settings
    val dialogScreenLocation = eventDevicePoint.locationOnScreen.also {
      it.translate(-initialOffset.x, -initialOffset.y)
    }
    view.location = dialogScreenLocation
  }

  private fun relocate(event: MouseEvent) {
    if (dragMoreButton != null) {
      val bounds = Rectangle(Point(), ComponentUtil.getWindow(myDragComponent)!!.size)
      if (event.x > bounds.width / 2) {
        bounds.x = 1 + 2 * bounds.width / 3
        dragMoreButtonNewSide = RIGHT
      }
      else {
        dragMoreButtonNewSide = LEFT
      }
      bounds.width /= 3
      dropTargetHighlightComponent.bounds = bounds
      relocateImageView(event)
      return
    }

    val eventDevicePoint = DevicePoint(event)
    val view = dragSession?.view ?: return
    val toolWindow = getToolWindow() ?: return

    relocateImageView(event)

    val preferredStripe = getSourceStripe(toolWindow.anchor, toolWindow.isSplitMode)
    val targetStripe = getTargetStripeByDropLocation(eventDevicePoint, preferredStripe)
                       ?: if (isPointInVisibleDockedToolWindow(eventDevicePoint)) preferredStripe else null
    lastStripe?.let {
      if (it != targetStripe) {
        removeDropTargetHighlighter(toolWindow.toolWindowManager.getToolWindowPane(it.paneId))
        it.resetDrop()
      }
    }

    if (isNewUi) {
      if (lastStripe == null) {
        clearDropTooltip()
      }
      else {
        val anchor = ToolWindowMoveAction.Anchor.getAnchor(lastStripe!!.anchor, lastStripe!!.getDropToSide() == true)
        if (anchor !== lastDropTooltipAnchor) {
          clearDropTooltip()
          lastDropTooltipAnchor = anchor

          val tooltip = HelpTooltip()
          tooltip.setTitle(UIBundle.message("tool.window.move.to.action.group.name") + " " + anchor.toString())
          dropTooltipPopup = HelpTooltip.initPopupBuilder(tooltip.createTipPanel()).createPopup()
        }

        val bounds = view.bounds
        val size = dropTooltipPopup!!.content.preferredSize
        val location = Point(0, bounds.y + (bounds.height - size.height) / 2)

        if (anchor.toString().lowercase().contains("left")) {
          location.x = bounds.x + bounds.width + JBUI.scale(10)
        }
        else {
          location.x = bounds.x - JBUI.scale(10) - size.width
        }

        if (dropTooltipPopup!!.isVisible) {
          dropTooltipPopup!!.setLocation(location)
        }
        else {
          dropTooltipPopup!!.show(RelativePoint(location))
        }
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
      targetStripe.processDropButton(initialStripeButton, view.asDragButton(), eventDevicePoint)

      if (lastDropTargetPaneId != targetStripe.paneId) {
        lastDropTargetPaneId = targetStripe.paneId
        lastDropTargetPane = toolWindow.toolWindowManager.getToolWindowPane(targetStripe.paneId)
      }

      SwingUtilities.invokeLater(Runnable {
        if (initialAnchor == null || initialIsSplit == null) return@Runnable

        // Get the bounds of the drop target highlight. If the point is inside the drop target bounds, use those bounds. If it's not, but
        // it's inside the bounds of the tool window (and the tool window is visible), then use the tool window bounds. Note that when
        // docked, the tool window's screen coordinate system will be the same as the mouse event's. But if it's floating, it might be on
        // another screen (although if it's floating, we don't get bounds)
        val bounds = if (isNewUi) {
          targetStripe.getToolWindowDropAreaScreenBounds()
        }
        else {
          val toolWindowBounds = getToolWindowScreenBoundsIfVisibleAndDocked(toolWindow)?.takeIf {
            getTargetStripeByDropLocation(eventDevicePoint, preferredStripe) == null && it.contains(event.locationOnScreen)
          }
          toolWindowBounds ?: getDropTargetScreenBounds(lastDropTargetPane!!, targetStripe.anchor)
        }
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

  private fun setDragOut(dragOut: Boolean) {
    dragSession?.setDragOut(dragOut)
    dropTargetHighlightComponent.isVisible = !dragOut
  }

  override fun processDragOutFinish(event: MouseEvent) = processDragFinish(event, false)

  override fun processDragFinish(event: MouseEvent, willDragOutStart: Boolean) {
    if (!checkModifiers(event)) {
      stopDrag()
      return
    }
    if (dragMoreButton != null) {
      if (dragMoreButton!!.side !== dragMoreButtonNewSide) {
        ToolWindowManagerEx.getInstanceEx((dragSourcePane.frame as IdeFrame).project!!).setMoreButtonSide(dragMoreButtonNewSide!!)
      }
      stopDrag()
      return
    }
    val toolWindow = getToolWindow() ?: return
    if (willDragOutStart) {
      return
    }

    try {
      val devicePoint = DevicePoint(event)
      val preferredStripe = getSourceStripe(initialAnchor!!, initialIsSplit!!)

      // If the drop point is not inside a stripe bounds, but is inside the visible tool window bounds, do nothing - we're not moving.
      // Note that we must check the stripe because we might be moving from top to bottom or left to right
      if (getTargetStripeByDropLocation(devicePoint, preferredStripe) == null && isPointInVisibleDockedToolWindow(devicePoint)) {
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
                                                            isSplit = info?.isSplit ?: preferredStripe.split)
        }
      }
    }
    finally {
      stopDrag()
    }
  }

  override fun processDragOutCancel() = stopDrag()
  override fun processDragCancel() = stopDrag()

  override fun mouseReleased(e: MouseEvent?) {
    super.mouseReleased(e)
    toolWindowRef = null
  }

  override fun stop() {
    super.stop()
    // Would stop ever be called in the middle of a drag? This implies the project is disposed mid-drag
    stopDrag()
  }

  private fun stopDrag() {
    val window = getToolWindow()
    window?.let { overlayStripesIfHidden(it, false) }
    getStripeButtonForToolWindow(window)?.isVisible = true
    dragSourcePane.buttonManager.stopDrag()
    lastDropTargetPane?.let { removeDropTargetHighlighter(it) }
    dropTargetHighlightComponent.bounds = Rectangle(0, 0, 0, 0)

    @Suppress("SSBasedInspection")
    dragSession?.stop()
    dragSession = null
    toolWindowRef = null
    initialAnchor = null
    initialIsSplit = null
    lastStripe?.resetDrop()
    lastStripe = null
    lastDropTargetPaneId = null
    lastDropTargetPane = null
    initialStripeButton = null
    dragMoreButton?.setDragState(false)
    dragMoreButton = null
    dragMoreButtonNewSide = null
    clearDropTooltip()
  }

  private fun clearDropTooltip() {
    val popup = dropTooltipPopup
    dropTooltipPopup = null
    lastDropTooltipAnchor = null

    if (popup != null && popup.isVisible) {
      popup.cancel()
    }
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

  private fun getComponentFromDragSourcePane(point: RelativePoint) : Component? {
    // This is VERY tricky. Can't use dragSourcePane directly here because it can be obscured by a popup (IDEA-329995).
    // Can't use the window (IdeFrame) either because in that case we can mistakenly end up selecting the glass pane,
    // because we place that drop target highlight component on it (see createDropTargetHighlightComponent).
    // So we have to walk the middle ground here and start searching from the layered pane instead.
    // Moreover, we want the topmost layered pane, as there may be others, like the tool window pane itself.
    val layeredPane = getTopmostLayeredPane(dragSourcePane) ?: return null
    val pointOnWindow = point.getPoint(layeredPane)
    return SwingUtilities.getDeepestComponentAt(layeredPane, pointOnWindow.x, pointOnWindow.y)
  }

  private fun getTopmostLayeredPane(dragSourcePane: ToolWindowPane): JLayeredPane? {
    var result: JLayeredPane? = null
    var component: Component? = dragSourcePane
    while (component != null) {
      if (component is JLayeredPane) {
        result = component
      }
      component = component.parent
    }
    return result
  }

  private fun getToolWindowAtPoint(point: RelativePoint): ToolWindowImpl? {
    val clickedComponent = getComponentFromDragSourcePane(point)
    if (clickedComponent != null && isComponentDraggable(clickedComponent)) {
      val decorator = InternalDecoratorImpl.findNearestDecorator(clickedComponent)
      if (decorator != null &&
          isHeaderDraggingEnabled() &&
          (decorator.toolWindow.anchor != BOTTOM ||
           decorator.locationOnScreen.y < point.screenPoint.y - ToolWindowPane.headerResizeArea))
        return decorator.toolWindow
    }

    return when (clickedComponent) {
      is StripeButton -> clickedComponent.toolWindow
      is SquareStripeButton -> clickedComponent.toolWindow
      is ToolWindowProvider -> clickedComponent.toolWindow
      else -> null
    }
  }

  private fun isHeaderDraggingEnabled(): Boolean = AdvancedSettings.getBoolean("ide.tool.window.header.dnd")

  private fun getPaneContentScreenBounds(pane: ToolWindowPane): Rectangle {
    val location = pane.locationOnScreen
    if (isNewUi) {
      return Rectangle(location.x, location.y, pane.width, pane.height)
    }
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
    if (!toolWindow.isVisible || !toolWindow.type.isInternal) return null

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

      val isLeft = component.isOnTheLeftStripe()
      val areaSize = when (component) {
        is StripeButton -> component.size.also {
          val delta = JBUIScale.scale(1)
          it.width -= delta
          it.height -= delta
        }
        is AbstractSquareStripeButton -> component.size.also {
          JBInsets.removeFrom(it, component.insets)
          JBInsets.removeFrom(it, SquareStripeButtonLook.getIconPadding(isLeft))
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
          is AbstractSquareStripeButton -> component.paintDraggingButton(it, isLeft)
        }

        it.dispose()
      }
      return image
    }
    finally {
      component.bounds = initialBounds
    }
  }
  
  private class DragSession(
    val view: DragImageView,
    private val stripeButtonImage: BufferedImage,
    private val toolWindowThumbnailImage: BufferedImage?,
  ) {
    private var dragOut: Boolean? = null
    
    init {
      setDragOut(false)
    }

    fun setDragOut(dragOut: Boolean) {
      if (dragOut == this.dragOut) return
      this.dragOut = dragOut
      val image = if (dragOut && toolWindowThumbnailImage != null) toolWindowThumbnailImage else stripeButtonImage
      view.image = image
    }

    fun start() {
      view.show()
    }

    fun stop() {
      view.hide()
    }
  }

  private class DragImageDialog(
    owner: JComponent,
    val helper: ToolWindowDragHelper,
  ) : JDialog(ComponentUtil.getWindow(owner), null, ModalityType.MODELESS), DialogWithImage {

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
    }

    override var image: Image? = null
      set(value) {
        field = value
        with(contentPane as JLabel) {
          icon = value?.let { IconUtil.createImageIcon(it) }
          revalidate()
          pack()
          repaint()
        }
      }
  }

  interface ToolWindowProvider {
    val toolWindow: ToolWindowImpl?
  }
}