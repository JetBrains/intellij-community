// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.IdeGlassPaneUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.wayland.getValidBoundsForPopup
import com.intellij.ui.wayland.isUnconstrainedPopupPositioning
import com.intellij.util.BitUtil
import com.intellij.util.ui.JdkConstants
import com.intellij.util.ui.StartupUiUtil
import com.jetbrains.JBR
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Cursor
import java.awt.Dialog
import java.awt.Frame
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference
import javax.swing.SwingUtilities

internal interface WindowMouseListenerSource {
  fun getContent(event: MouseEvent): Component

  fun getView(event: Component): Component?

  fun isDisabled(view: Component): Boolean

  @JdkConstants.CursorType
  fun getCursorType(view: Component?, location: Point?): Int

  fun setCursor(content: Component, cursor: Cursor?)

  fun updateBounds(bounds: Rectangle, view: Component, dx: Int, dy: Int)

  fun notifyMoved()

  fun notifyResized()
}

internal fun createWindowMouseListenerSupport(source: WindowMouseListenerSource): WindowMouseListenerSupport {
  return if (StartupUiUtil.isWaylandToolkit()) {
    WaylandWindowMouseListenerSupport(source)
  }
  else {
    RegularWindowMouseListenerSupport(source)
  }
}

internal sealed class WindowMouseListenerSupport(private val source: WindowMouseListenerSource) {
  private var debugKind: String? = null
  private var compRef: WeakReference<Component>? = null

  protected val d: String
    get() {
      val window = ComponentUtil.getWindow(compRef?.get())
      return "$debugKind ${window?.javaClass?.name}@${System.identityHashCode(window)}"
    }

  @get:JdkConstants.CursorType
  var cursorType: Int = 0
    private set
  protected var currentSession: WindowListenerSession? = null
    private set

  var leftMouseButtonOnly = false
  
  val isBusy: Boolean
    get() = currentSession != null

  protected abstract fun moveAfterMouseRelease(): Boolean

  protected abstract fun jbrMoveSupported(component: Component?): Boolean

  fun installForResizeListener(glassPane: IdeGlassPane) {
    if (glassPane is Component) {
      debugKind = "resize"
      compRef = WeakReference(glassPane)
    }
  }

  fun installForMoveListener(component: Component) {
    debugKind = "move"
    compRef = WeakReference(component)
  }

  fun uninstall() {
    stop()
    debugKind = null
    compRef = null
  }

  /**
   * Updates a cursor and starts moving/resizing if the `start` is specified.
   */
  fun update(event: MouseEvent, start: Boolean) {
    LOG.debug { "$d update($event, $start)" }
    if (event.isConsumed || (start && leftMouseButtonOnly && !SwingUtilities.isLeftMouseButton(event))) {
      return
    }

    if (isBusy && start) {
      LOG.debug {
        "$d Received a mouse pressed event while in a drag session. " +
        "This means that the release event was missed, cleaning up the previous session"
      }
      stop()
    }

    if (!isBusy) {
      val content = source.getContent(event)
      val view = source.getView(content)
      if (view != null && view.isShowing) {
        beforeUpdate(event, view)
        updateCursor(event, content, view)
        if (start && cursorType != Cursor.CUSTOM_CURSOR) {
          start(event, view, content)
          event.consume()
        }
      }
    }
  }

  protected open fun beforeUpdate(event: MouseEvent, view: Component) { }

  private fun updateCursor(event: MouseEvent, content: Component, view: Component) {
    cursorType = if (source.isDisabled(view)) Cursor.CUSTOM_CURSOR else source.getCursorType(view, event.locationOnScreen)
    source.setCursor(content, Cursor.getPredefinedCursor(if (cursorType == Cursor.CUSTOM_CURSOR) Cursor.DEFAULT_CURSOR else cursorType))
  }

  private fun start(event: MouseEvent, view: Component, content: Component) {
    val newSession = WindowListenerSession(
      view = view,
      content = content,
      location = event.locationOnScreen,
      viewBounds = view.bounds,
      mouseButton = event.getButton(),
      stopOnHideListener = setupStopOnHideListener(view),
    )
    currentSession = newSession
    LOG.debug { "$d Starting a new move/resize session: $currentSession" }
    onStarted(event, view, newSession)
  }

  private fun setupStopOnHideListener(view: Component): HierarchyListener {
    check(view.isShowing) { "Move/resize sessions should only start when the view is showing: $view" }
    val showingListener = HierarchyListener { event ->
      if (BitUtil.isSet(event.changeFlags, HierarchyEvent.SHOWING_CHANGED.toLong()) && !view.isShowing) {
        LOG.debug { "$d Stopping the session because the view is no longer showing" }
        stop()
      }
    }
    view.addHierarchyListener(showingListener)
    return showingListener
  }

  protected open fun onStarted(event: MouseEvent, view: Component, newSession: WindowListenerSession) { }

  /**
   * Processes moving/resizing and stops it if the current event is not `MouseEvent.MOUSE_DRAGGED`.
   */
  fun process(event: MouseEvent) {
    LOG.debug { "$d process($event)" }
    // Non-drag "stop" (mouse released and mouse clicked) are always handled both ways, even on Wayland,
    // as it's the most robust way: one channel may miss it (e.g., because something consumed it), the other will (hopefully) get it.
    if (event.id != MouseEvent.MOUSE_DRAGGED || shouldHandleSessionEventDirectly(event)) {
      handleSessionEvent(event)
    }
  }

  protected abstract fun shouldHandleSessionEventDirectly(event: MouseEvent): Boolean

  protected fun handleSessionEvent(event: MouseEvent) {
    val isDragEvent = event.id == MouseEvent.MOUSE_DRAGGED
    // Some points:
    // 1. Stop events should be handled even if they're already consumed, so the session finishes correctly.
    // 2. Non-drag unconsumed events should be handled as drag events anyway to support the "move on mouse release" mode.
    if (!event.isConsumed) {
      handleDragEvent(event, isDragEvent)
    }
    if (!isDragEvent) {
      handleStopEvent(event)
    }
  }

  @JdkConstants.CursorType
  open fun getResizeCursor(top: Int, left: Int, bottom: Int, right: Int, resizeArea: Insets): Int {
    if (top < resizeArea.top) {
      if (left < resizeArea.left * 2) return Cursor.NW_RESIZE_CURSOR
      if (right < resizeArea.right * 2) return Cursor.NE_RESIZE_CURSOR
      return Cursor.N_RESIZE_CURSOR
    }
    if (bottom < resizeArea.bottom) {
      if (left < resizeArea.left * 2) return Cursor.SW_RESIZE_CURSOR
      if (right < resizeArea.right * 2) return Cursor.SE_RESIZE_CURSOR
      return Cursor.S_RESIZE_CURSOR
    }
    if (left < resizeArea.left) {
      if (top < resizeArea.top * 2) return Cursor.NW_RESIZE_CURSOR
      if (bottom < resizeArea.bottom * 2) return Cursor.SW_RESIZE_CURSOR
      return Cursor.W_RESIZE_CURSOR
    }
    if (right < resizeArea.right) {
      if (top < resizeArea.top * 2) return Cursor.NE_RESIZE_CURSOR
      if (bottom < resizeArea.bottom * 2) return Cursor.SE_RESIZE_CURSOR
      return Cursor.E_RESIZE_CURSOR
    }
    return Cursor.CUSTOM_CURSOR
  }

  protected fun handleDragEvent(event: MouseEvent, isDragEvent: Boolean) {
    LOG.debug { "$d handleDragEvent($event, $isDragEvent)" }
    val session = currentSession ?: return
    if (isDragEvent) {
      onDraggingStarted(session)
    }

    val view = session.view // On Wayland, we can't trust the event source.
    LOG.trace { "$d handleDragEvent: view=$view" }
    // Enter in move mode only after mouse move, so double click is supported
    if (isDragEvent && cursorType == Cursor.DEFAULT_CURSOR && jbrMoveSupported(view)) {
      LOG.debug("$d Entering the move-with-the-mouse mode")
      enterJbrMoveMode(session, view)
      return
    }

    // Start the override only on the first drag: a click also starts a session, and the JBR move mode sets its own cursor.
    if (isDragEvent && session.cursorOverride == null) {
      LOG.debug { "$d Overriding the cursor in all windows" }
      session.cursorOverride = IdeGlassPaneUtil.overrideCursorInAllWindows(Cursor.getPredefinedCursor(cursorType))
    }

    val bounds = Rectangle(session.viewBounds)
    val delta = computeOffsetFromInitialLocation(session, event)
    if (cursorType == Cursor.DEFAULT_CURSOR && view is Frame) {
      val state = view.extendedState
      if (WindowMouseListener.isStateSet(Frame.MAXIMIZED_HORIZ, state)) delta.x = 0
      if (WindowMouseListener.isStateSet(Frame.MAXIMIZED_VERT, state)) delta.y = 0
    }
    val oldBounds = Rectangle(bounds)
    LOG.trace { "$d handleDragEvent: old view bounds: $bounds" }
    source.updateBounds(bounds, view, delta.x, delta.y)
    LOG.trace { "$d handleDragEvent: updated view bounds: $bounds" }
    makeChangeValid(oldBounds, bounds, view)
    LOG.trace { "$d handleDragEvent: valid updated view bounds: $bounds" }
    val currentViewBounds = view.bounds
    LOG.trace { "$d handleDragEvent: current view bounds: $currentViewBounds" }
    if (bounds != currentViewBounds) {
      val moved = bounds.x != currentViewBounds.x || bounds.y != currentViewBounds.y
      val resized = bounds.width != currentViewBounds.width || bounds.height != currentViewBounds.height
      val reallyMoveWindow = !moveAfterMouseRelease() || !isDragEvent
      if ((moved && reallyMoveWindow) || resized) {
        LOG.debug { "$d Applying the new bounds: $bounds" }
        setViewBounds(view, bounds, moved, resized)
      }
    }
    event.consume()
  }

  protected open fun makeChangeValid(oldBounds: Rectangle, newBounds: Rectangle, view: Component) { }

  protected fun onDraggingStarted(session: WindowListenerSession) {
    // No "clicked" event after dragging.
    // On Wayland it used to be different, but was fixed in JBR-9803.
    session.expectMouseClicked = false
  }

  protected open fun computeOffsetFromInitialLocation(session: WindowListenerSession, event: MouseEvent): Point {
    val location = session.location
    val dx: Int = event.xOnScreen - location.x
    val dy: Int = event.yOnScreen - location.y
    val offset = Point(dx, dy)
    LOG.debug { "$d Relative mouse movement computed: ${event.locationOnScreen} - $location = $offset" }
    return offset
  }

  private fun setViewBounds(view: Component, bounds: Rectangle, moved: Boolean, resized: Boolean) {
    view.reshape(bounds.x, bounds.y, bounds.width, bounds.height)
    view.invalidate()
    view.validate()
    view.repaint()
    if (moved) source.notifyMoved()
    if (resized) source.notifyResized()
  }

  protected fun handleStopEvent(event: MouseEvent) {
    val session = currentSession ?: return

    resetCursor(session.content)

    if (session.expectMouseReleased && event.id == MouseEvent.MOUSE_RELEASED) {
      session.expectMouseReleased = false
      event.consume()
    }
    if (session.expectMouseClicked && event.id == MouseEvent.MOUSE_CLICKED) {
      session.expectMouseClicked = false
      event.consume()
    }

    if (!session.expectMouseReleased && !session.expectMouseClicked) {
      stop()
    }
  }

  private fun resetCursor(content: Component) {
    source.setCursor(content, Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
  }

  protected fun stop() {
    val stoppedSession = currentSession ?: return
    LOG.debug { "$d The move/resize session has finished: $stoppedSession" }
    currentSession = null
    stoppedSession.view.removeHierarchyListener(stoppedSession.stopOnHideListener)
    stoppedSession.cursorOverride?.let { cursorOverride ->
      LOG.debug { "$d Removing the cursor override" }
      cursorOverride.close()
    }
    onStopped(stoppedSession)
  }

  protected open fun onStopped(stoppedSession: WindowListenerSession) { }

  private fun enterJbrMoveMode(session: WindowListenerSession, view: Component?) {
    JBR.getWindowMove().startMovingTogetherWithMouse(view as Window, session.mouseButton)
    stop()
  }

  protected data class WindowListenerSession(
    val view: Component,
    val content: Component,
    val location: Point,
    val viewBounds: Rectangle,
    val mouseButton: Int,
    val stopOnHideListener: HierarchyListener,
    var expectMouseReleased: Boolean = true,
    var expectMouseClicked: Boolean = true,
    var cursorOverride: AutoCloseable? = null,
    var toolkitListener: AWTEventListener? = null,
  )
}

private class RegularWindowMouseListenerSupport(source: WindowMouseListenerSource) : WindowMouseListenerSupport(source) {
  override fun shouldHandleSessionEventDirectly(event: MouseEvent): Boolean = true

  override fun moveAfterMouseRelease(): Boolean = false

  override fun jbrMoveSupported(component: Component?): Boolean {
    return (component is Frame || component is Dialog) && JBR.isWindowMoveSupported()
  }
}

private class WaylandWindowMouseListenerSupport(source: WindowMouseListenerSource) : WindowMouseListenerSupport(source) {
  private var isTruePopup = false
  private var dx = 0
  private var dy = 0
  private var grabPoint = Point()

  override fun beforeUpdate(event: MouseEvent, view: Component) {
    isTruePopup = view is Window && view.type == Window.Type.POPUP
  }

  override fun onStarted(event: MouseEvent, view: Component, newSession: WindowListenerSession) {
    dx = 0
    dy = 0
    if (isRelativeMovementMode()) {
      (view as Window).isUnconstrainedPopupPositioning = true
      grabPoint = RelativePoint(event).getPoint(view)
      @Suppress("UsePropertyAccessSyntax")
      JBR.getRelativePointerMovement().getAccumulatedMouseDeltaAndReset()
      newSession.toolkitListener = setupToolkitListener()
    }
  }

  private fun setupToolkitListener(): AWTEventListener {
    val toolkit = Toolkit.getDefaultToolkit()
    val globalToolkitListener = AWTEventListener { event ->
      handleToolkitEvent(event)
    }
    LOG.debug { "$d Adding the toolkit listener" }
    toolkit.addAWTEventListener(globalToolkitListener, AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK)
    return globalToolkitListener
  }

  private fun handleToolkitEvent(event: AWTEvent) {
    if (
      event is MouseEvent &&
      (
        event.id == MouseEvent.MOUSE_DRAGGED ||
        event.id == MouseEvent.MOUSE_RELEASED ||
        event.id == MouseEvent.MOUSE_CLICKED
      ) &&
      !shouldHandleSessionEventDirectly(event)
    ) {
      LOG.debug { "$d Session event from toolkit: $event" }
      handleSessionEvent(event)
    }
  }

  override fun onStopped(stoppedSession: WindowListenerSession) {
    LOG.debug { "$d Removing the toolkit listener" }
    val toolkit = Toolkit.getDefaultToolkit()
    toolkit.removeAWTEventListener(stoppedSession.toolkitListener)
  }

  override fun getResizeCursor(top: Int, left: Int, bottom: Int, right: Int, resizeArea: Insets): Int {
    if (isRelativeMovementMode()) return super.getResizeCursor(top, left, bottom, right, resizeArea)
    // Wayland doesn't allow to change window's location programmatically,
    // so resizing from top/left shall be forbidden for now.
    if (bottom < resizeArea.bottom) {
      if (right < resizeArea.right * 2) return Cursor.SE_RESIZE_CURSOR
      return Cursor.S_RESIZE_CURSOR
    }
    if (right < resizeArea.right) {
      if (bottom < resizeArea.bottom * 2) return Cursor.SE_RESIZE_CURSOR
      return Cursor.E_RESIZE_CURSOR
    }
    return Cursor.CUSTOM_CURSOR
  }

  override fun computeOffsetFromInitialLocation(session: WindowListenerSession, event: MouseEvent): Point {
    if (isRelativeMovementMode()) {
      @Suppress("UsePropertyAccessSyntax")
      val delta = JBR.getRelativePointerMovement().getAccumulatedMouseDeltaAndReset()
      dx += delta.x
      dy += delta.y
      val offset = Point(dx, dy)
      LOG.debug { "$d Relative mouse movement from the protocol: $delta, new offset is $offset" }
      return offset
    }
    else {
      return super.computeOffsetFromInitialLocation(session, event)
    }
  }

  override fun makeChangeValid(oldBounds: Rectangle, newBounds: Rectangle, view: Component) {
    if (!isRelativeMovementMode()) return

    val windowBounds = getValidBoundsForPopup(view) ?: return

    val newBoundsBeforeFit = Rectangle(newBounds)

    fitPopupBounds(oldBounds, newBounds, windowBounds)

    // If the popup was moved, don't let the initial grab point move away from the owning window.
    // Because if we let it move away from the owning window, it may also very well move outside the screen.
    // And that's bad, because it will be impossible to grab it again by the same point (in the worst case, any point).
    // And because we have no idea where the screen is (thanks, Wayland), this seems to be the only way.
    if (newBounds.x != oldBounds.x || newBounds.y != oldBounds.y) {
      fitGrabPoint(newBounds, windowBounds)
    }

    // If we limit the popup movement, pretend that the mouse movement was limited too.
    // Otherwise, moving the mouse in the opposite direction will work with a "delay,"
    // because it'll have to "catch up" its previous position before starting doing anything useful.
    dx += newBounds.x - newBoundsBeforeFit.x
    dy += newBounds.y - newBoundsBeforeFit.y
  }

  private fun fitPopupBounds(oldBounds: Rectangle, newBounds: Rectangle, windowBounds: Rectangle) {
    LOG.debug { "$d Trying to fit the popup into $windowBounds after change $oldBounds -> $newBounds" }
    val newBoundsBeforeFit = if (LOG.isDebugEnabled) Rectangle(newBounds) else null

    // Moving or resized, for each axis either the leftmost or the rightmost coordinate might have changed, but not both.
    // This means we can calculate X/Y based on unchanged width/height and the other way.
    val minX = windowBounds.x - (newBounds.width - 1) // must not move completely outside to the left
    val maxX = windowBounds.x + (windowBounds.width - 1) // must not move completely outside to the right
    val minWidth = windowBounds.x - newBounds.x + 1 // must not resize so that the right edge moves outside to the left
    val minY = windowBounds.y - (newBounds.height - 1) // ditto for Y
    val maxY = windowBounds.y + (windowBounds.height - 1)
    val minHeight = windowBounds.y - newBounds.y + 1
    LOG.debug { "$d The allowed value by parent bounds are: x=$minX..$maxX, width>=$minWidth, y=$minY..$maxY, height>=$minHeight" }

    if (newBounds.x != oldBounds.x) { // moved or resized from the left
      newBounds.x = newBounds.x.coerceIn(minX..maxX)
    }
    else if (newBounds.width != oldBounds.width) { // resized from the right
      newBounds.width = newBounds.width.coerceAtLeast(minWidth)
    }
    if (newBounds.y != oldBounds.y) { // moved or resized from the top
      newBounds.y = newBounds.y.coerceIn(minY..maxY)
    }
    else if (newBounds.height != oldBounds.height) { // resized from the bottom
      newBounds.height = newBounds.height.coerceAtLeast(minHeight)
    }

    if (newBoundsBeforeFit != null && newBounds != newBoundsBeforeFit) {
      LOG.debug { "$d New bounds after fitting: $newBoundsBeforeFit -> $newBounds" }
    }
  }

  private fun fitGrabPoint(newBounds: Rectangle, validBounds: Rectangle) {
    LOG.debug { "$d Trying to fit the grab point $grabPoint (relative to $newBounds) into $validBounds" }
    val newBoundsBeforeFit = if (LOG.isDebugEnabled) Rectangle(newBounds) else null

    val minX = validBounds.x
    val maxX = validBounds.x + validBounds.width - 1
    val grabX = newBounds.x + grabPoint.x
    val fitGrabX = grabX.coerceIn(minX, maxX)
    newBounds.x += fitGrabX - grabX

    val minY = validBounds.y
    val maxY = validBounds.y + validBounds.height - 1
    val grabY = newBounds.y + grabPoint.y
    val fitGrabY = grabY.coerceIn(minY, maxY)
    newBounds.y += fitGrabY - grabY

    if (newBoundsBeforeFit != null && newBounds != newBoundsBeforeFit) {
      LOG.debug { "$d New bounds after fitting the grab point: $newBoundsBeforeFit -> $newBounds" }
    }
  }

  override fun shouldHandleSessionEventDirectly(event: MouseEvent): Boolean {
    // While the popup is moving, Wayland can't be trusted to correctly deliver events to it.
    // But when we're in a popup drag session, we should handle all events anyway.
    // So we handle events directly only if:
    // 1. there's no session => we can't tell if the event is related to this component;
    // 2. native popup movement is disabled => no need for these tricks, the popup will only move when the session is over.
    // Otherwise, we receive events directly through the Toolkit and ignore their source (the real source is stored in the session).
    if (currentSession == null) {
      LOG.debug { "$d Not handling the toolkit event because there's no current session" }
      return true
    }
    if (!isRelativeMovementMode()) {
      LOG.debug { "$d Not handling the toolkit event because the relative movement mode is disabled" }
      return true
    }
    LOG.debug { "$d Toolkit event handling will be used for $event" }
    return false
  }

  private fun isRelativeMovementMode(): Boolean =
    isTruePopup &&
    JBR.isRelativePointerMovementSupported() &&
    Registry.`is`("wayland.relative.mouse.protocol", defaultValue = true)

  override fun moveAfterMouseRelease(): Boolean = !isRelativeMovementMode()

  override fun jbrMoveSupported(component: Component?): Boolean {
    return component is Window && component.type != Window.Type.POPUP
  }
}

private val LOG = logger<WindowMouseListenerSupport>()
