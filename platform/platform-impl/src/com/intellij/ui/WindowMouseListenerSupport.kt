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
  private var location: Point? = null
  private var viewBounds: Rectangle? = null
  private var mouseButton = 0
  private var wasDragged = false
  
  var leftMouseButtonOnly = false
  
  val isBusy: Boolean
    get() = location != null

  protected abstract fun moveAfterMouseRelease(): Boolean

  protected abstract fun jbrMoveSupported(component: Component?): Boolean

  /**
   * Updates a cursor and starts moving/resizing if the `start` is specified.
   */
  fun update(event: MouseEvent, start: Boolean) {
    if (event.isConsumed || (start && leftMouseButtonOnly && !SwingUtilities.isLeftMouseButton(event))) {
      return
    }

    if (start) wasDragged = false // reset dragged state when mouse pressed

    if (location == null) {
      val content = source.getContent(event)
      val view = source.getView(content)
      if (view != null) {
        cursorType = if (source.isDisabled(view)) Cursor.CUSTOM_CURSOR else source.getCursorType(view, event.locationOnScreen)
        source.setCursor(content, Cursor.getPredefinedCursor(if (cursorType == Cursor.CUSTOM_CURSOR) Cursor.DEFAULT_CURSOR else cursorType))
        if (start && cursorType != Cursor.CUSTOM_CURSOR) {
          mouseButton = event.getButton()
          location = event.locationOnScreen
          viewBounds = view.bounds
          event.consume()
        }
      }
    }
  }

  /**
   * Processes moving/resizing and stops it if not `mouseMove`.
   */
  fun process(event: MouseEvent, mouseMove: Boolean) {
    if (event.isConsumed) return
    if (mouseMove) wasDragged = true // set dragged state when mouse dragged

    val viewBounds = this.viewBounds
    val location = this.location
    if (location != null && viewBounds != null) {
      val content = source.getContent(event)
      val view = source.getView(content)
      if (mouseMove && cursorType == Cursor.DEFAULT_CURSOR && jbrMoveSupported(view)) {
        // Enter in move mode only after mouse move, so double click is supported
        JBR.getWindowMove().startMovingTogetherWithMouse(view as Window, mouseButton)
        this.location = null
        this.viewBounds = null
        return
      }

      if (view != null) {
        val bounds = Rectangle(viewBounds)
        var dx: Int = event.xOnScreen - location.x
        var dy: Int = event.yOnScreen - location.y
        if (cursorType == Cursor.DEFAULT_CURSOR && view is Frame) {
          val state = view.extendedState
          if (WindowMouseListener.isStateSet(Frame.MAXIMIZED_HORIZ, state)) dx = 0
          if (WindowMouseListener.isStateSet(Frame.MAXIMIZED_VERT, state)) dy = 0
        }
        source.updateBounds(bounds, view, dx, dy)
        val currentViewBounds = view.bounds
        if (bounds != currentViewBounds) {
          val moved = bounds.x != currentViewBounds.x || bounds.y != currentViewBounds.y
          val resized = bounds.width != currentViewBounds.width || bounds.height != currentViewBounds.height
          val reallyMoveWindow = !moveAfterMouseRelease() || !mouseMove
          if ((moved && reallyMoveWindow) || resized) {
            view.reshape(bounds.x, bounds.y, bounds.width, bounds.height)
            view.invalidate()
            view.validate()
            view.repaint()
            if (moved) source.notifyMoved()
            if (resized) source.notifyResized()
          }
        }
      }
      if (!mouseMove) {
        source.setCursor(content, Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
        this.location = null
        if (wasDragged) {
          this.viewBounds = null // no mouse clicked when mouse released after mouse dragged
        }
      }
      event.consume()
    }
    else if (!mouseMove && viewBounds != null) {
      this.viewBounds = null // consume mouse clicked for consumed mouse released if no mouse dragged
      event.consume()
    }
  }
}

internal class RegularWindowMouseListenerSupport(source: WindowMouseListenerSource) : WindowMouseListenerSupport(source) {
  override fun moveAfterMouseRelease(): Boolean = false

  override fun jbrMoveSupported(component: Component?): Boolean {
    return (component is Frame || component is Dialog) && JBR.isWindowMoveSupported()
  }
}

internal class WaylandWindowMouseListenerSupport(source: WindowMouseListenerSource) : WindowMouseListenerSupport(source) {
  override fun moveAfterMouseRelease(): Boolean = true

  override fun jbrMoveSupported(component: Component?): Boolean {
    return component is Window && component.type != Window.Type.POPUP
  }
}
