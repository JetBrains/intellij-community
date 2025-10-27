// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.util.ui.StartupUiUtil
import com.jetbrains.JBR
import org.intellij.lang.annotations.JdkConstants
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities


@ApiStatus.Internal
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
  @get:JdkConstants.CursorType
  var cursorType: Int = 0
    private set
  private var currentSession: WindowListenerSession? = null

  var leftMouseButtonOnly = false
  
  val isBusy: Boolean
    get() = currentSession != null

  protected abstract fun moveAfterMouseRelease(): Boolean

  protected abstract fun jbrMoveSupported(component: Component?): Boolean

  /**
   * Updates a cursor and starts moving/resizing if the `start` is specified.
   */
  fun update(event: MouseEvent, start: Boolean) {
    if (event.isConsumed || (start && leftMouseButtonOnly && !SwingUtilities.isLeftMouseButton(event))) {
      return
    }

    if (!isBusy) {
      val content = source.getContent(event)
      val view = source.getView(content)
      if (view != null) {
        beforeUpdate(event, view)
        updateCursor(event, content, view)
        if (start && cursorType != Cursor.CUSTOM_CURSOR) {
          start(event, view)
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

  private fun start(event: MouseEvent, view: Component) {
    currentSession = WindowListenerSession(
      event.locationOnScreen,
      view.bounds,
      event.getButton(),
    )
    onStarted(event, view)
  }

  protected open fun onStarted(event: MouseEvent, view: Component) { }

  /**
   * Processes moving/resizing and stops it if not `mouseMove`.
   */
  fun process(event: MouseEvent, mouseMove: Boolean) {
    // Some points:
    // 1. Stop events should be handled even if they're already consumed, so the session finishes correctly.
    // 2. Non-drag unconsumed events should be handled as drag events anyway to support the "move on mouse release" mode.
    if (!event.isConsumed) {
      handleDragEvent(event, mouseMove)
    }
    if (!mouseMove) {
      handleStopEvent(event)
    }
  }

  @JdkConstants.CursorType
  open fun getResizeCursor(top: Int, left: Int, bottom: Int, right: Int, resizeArea: Insets): Int {
    // Wayland doesn't allow to change window's location programmatically,
    // so resizing from top/left shall be forbidden for now.
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

  private fun handleDragEvent(event: MouseEvent, mouseMove: Boolean) {
    val session = currentSession ?: return
    if (mouseMove) {
      onDraggingStarted(session)
    }

    val content = source.getContent(event)
    val view = source.getView(content)
    // Enter in move mode only after mouse move, so double click is supported
    if (mouseMove && cursorType == Cursor.DEFAULT_CURSOR && jbrMoveSupported(view)) {
      enterJbrMoveMode(session, view)
      return
    }

    if (view != null) {
      val bounds = Rectangle(session.viewBounds)
      val delta = computeOffsetFromInitialLocation(session, event)
      if (cursorType == Cursor.DEFAULT_CURSOR && view is Frame) {
        val state = view.extendedState
        if (WindowMouseListener.isStateSet(Frame.MAXIMIZED_HORIZ, state)) delta.x = 0
        if (WindowMouseListener.isStateSet(Frame.MAXIMIZED_VERT, state)) delta.y = 0
      }
      source.updateBounds(bounds, view, delta.x, delta.y)
      val currentViewBounds = view.bounds
      if (bounds != currentViewBounds) {
        val moved = bounds.x != currentViewBounds.x || bounds.y != currentViewBounds.y
        val resized = bounds.width != currentViewBounds.width || bounds.height != currentViewBounds.height
        val reallyMoveWindow = !moveAfterMouseRelease() || !mouseMove
        if ((moved && reallyMoveWindow) || resized) {
          setViewBounds(view, bounds, moved, resized)
        }
      }
    }
    event.consume()
  }

  protected abstract fun onDraggingStarted(session: WindowListenerSession)

  protected open fun computeOffsetFromInitialLocation(session: WindowListenerSession, event: MouseEvent): Point {
    val location = session.location
    val dx: Int = event.xOnScreen - location.x
    val dy: Int = event.yOnScreen - location.y
    return Point(dx, dy)
  }

  private fun setViewBounds(view: Component, bounds: Rectangle, moved: Boolean, resized: Boolean) {
    view.reshape(bounds.x, bounds.y, bounds.width, bounds.height)
    view.invalidate()
    view.validate()
    view.repaint()
    if (moved) source.notifyMoved()
    if (resized) source.notifyResized()
  }

  private fun handleStopEvent(event: MouseEvent) {
    val session = currentSession ?: return

    resetCursor(source.getContent(event))

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

  private fun stop() {
    currentSession = null
  }

  private fun enterJbrMoveMode(session: WindowListenerSession, view: Component?) {
    JBR.getWindowMove().startMovingTogetherWithMouse(view as Window, session.mouseButton)
    stop()
  }

  protected data class WindowListenerSession(
    val location: Point,
    val viewBounds: Rectangle,
    val mouseButton: Int,
    var expectMouseReleased: Boolean = true,
    var expectMouseClicked: Boolean = true,
  )
}

private class RegularWindowMouseListenerSupport(source: WindowMouseListenerSource) : WindowMouseListenerSupport(source) {
  override fun moveAfterMouseRelease(): Boolean = false

  override fun onDraggingStarted(session: WindowListenerSession) {
    session.expectMouseClicked = false // after dragging starts, there will be only one "released" event
  }

  override fun jbrMoveSupported(component: Component?): Boolean {
    return (component is Frame || component is Dialog) && JBR.isWindowMoveSupported()
  }
}

private class WaylandWindowMouseListenerSupport(source: WindowMouseListenerSource) : WindowMouseListenerSupport(source) {
  private var isTruePopup = false
  private var dx = 0
  private var dy = 0

  override fun beforeUpdate(event: MouseEvent, view: Component) {
    isTruePopup = view is Window && view.type == Window.Type.POPUP
  }

  override fun onStarted(event: MouseEvent, view: Component) {
    dx = 0
    dy = 0
    if (isRelativeMovementMode()) {
      @Suppress("UsePropertyAccessSyntax")
      JBR.getRelativePointerMovement().getAccumulatedMouseDeltaAndReset()
    }
  }

  override fun onDraggingStarted(session: WindowListenerSession) { } // on Wayland, whether dragging has started or not, both "released" and "clicked" events will arrive

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
      return Point(dx, dy)
    }
    else {
      return super.computeOffsetFromInitialLocation(session, event)
    }
  }

  private fun isRelativeMovementMode(): Boolean = isTruePopup && JBR.isRelativePointerMovementSupported()

  override fun moveAfterMouseRelease(): Boolean = !isRelativeMovementMode()

  override fun jbrMoveSupported(component: Component?): Boolean {
    return component is Window && component.type != Window.Type.POPUP
  }
}
