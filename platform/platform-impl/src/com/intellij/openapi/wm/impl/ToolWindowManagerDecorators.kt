// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.MnemonicHelper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.ThreeComponentsSplitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.WindowInfo
import com.intellij.openapi.wm.safeToolWindowPaneId
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.toolWindow.ToolWindowEntry
import com.intellij.toolWindow.ToolWindowPane
import com.intellij.ui.ScreenUtil
import java.awt.Component
import java.awt.Frame
import java.awt.Point
import java.awt.Rectangle
import javax.swing.JFrame
import javax.swing.RootPaneContainer

private val LOG = logger<ToolWindowManagerImpl>()

internal class ToolWindowManagerDecorators(
  private val manager: ToolWindowManagerImpl,
) {
  fun updateStateAndRemoveDecorator(state: WindowInfoImpl, entry: ToolWindowEntry, dirtyMode: Boolean) {
    saveFloatingOrWindowedState(entry, state)
    removeDecoratorWithoutUpdatingState(entry, state, dirtyMode)
  }

  fun removeExternalDecorators(entry: ToolWindowEntry) {
    entry.windowedDecorator?.let {
      entry.windowedDecorator = null
      Disposer.dispose(it)
      detachInternalDecorator(entry)
      return
    }

    entry.floatingDecorator?.let {
      entry.floatingDecorator = null
      it.dispose()
      detachInternalDecorator(entry)
      return
    }
  }

  fun saveFloatingOrWindowedState(entry: ToolWindowEntry, info: WindowInfoImpl) {
    entry.floatingDecorator?.let {
      info.floatingBounds = it.visibleWindowBounds
      info.isActiveOnStart = it.isActive
      return
    }

    entry.windowedDecorator?.let { windowedDecorator ->
      info.isActiveOnStart = windowedDecorator.isActive
      val frame = windowedDecorator.getFrame()
      val externalDecorator = entry.externalDecorator
      if (frame.isShowing && externalDecorator != null) {
        val maximized = (frame as JFrame).extendedState == Frame.MAXIMIZED_BOTH
        if (maximized) {
          frame.extendedState = Frame.NORMAL
          frame.invalidate()
          frame.revalidate()
        }

        info.floatingBounds = externalDecorator.visibleWindowBounds
        info.isMaximized = maximized
      }
      return
    }
  }

  fun addFloatingDecorator(entry: ToolWindowEntry, info: WindowInfo) {
    val frame = manager.getToolWindowPane(entry.toolWindow).frame
    val decorator = entry.toolWindow.getOrCreateDecoratorComponent()
    val floatingDecorator = FloatingDecorator(frame, decorator)
    floatingDecorator.apply(info)

    entry.floatingDecorator = floatingDecorator
    setExternalDecoratorBounds(info, floatingDecorator, decorator, frame)

    floatingDecorator.show()
  }

  fun addWindowedDecorator(entry: ToolWindowEntry, info: WindowInfo) {
    val app = ApplicationManager.getApplication()
    if (app.isHeadlessEnvironment || app.isUnitTestMode) {
      return
    }

    val id = entry.id
    val decorator = entry.toolWindow.getOrCreateDecoratorComponent()
    val windowedDecorator = WindowedDecorator(manager.project, title = "${entry.toolWindow.stripeTitle} - ${manager.project.name}", component = decorator)
    val window = windowedDecorator.getFrame()

    MnemonicHelper.init((window as RootPaneContainer).contentPane)

    val shouldBeMaximized = info.isMaximized
    setExternalDecoratorBounds(info, windowedDecorator, decorator, manager.getToolWindowPane(entry.toolWindow).frame)
    entry.windowedDecorator = windowedDecorator
    Disposer.register(windowedDecorator) {
      if (manager.idToEntry[id]?.windowedDecorator != null) {
        manager.hideToolWindow(id, false)
      }
    }

    window.isAutoRequestFocus = info.isActiveOnStart
    try {
      windowedDecorator.show(false)
    }
    finally {
      window.isAutoRequestFocus = true
    }

    val rootPane = (window as RootPaneContainer).rootPane
    val rootPaneBounds = rootPane.bounds
    val point = rootPane.locationOnScreen
    val windowBounds = window.bounds
    LOG.debug {
      "Adjusting the bounds of the windowed tool window ${info.id} according to " +
      "its bounds ($windowBounds) and its root pane bounds ($rootPaneBounds)"
    }
    window.setLocation(2 * windowBounds.x - point.x, 2 * windowBounds.y - point.y)
    window.setSize(2 * windowBounds.width - rootPaneBounds.width, 2 * windowBounds.height - rootPaneBounds.height)
    LOG.debug { "The adjusted bounds are ${window.bounds}" }
    if (shouldBeMaximized && window is Frame) {
      window.extendedState = Frame.MAXIMIZED_BOTH
      LOG.debug { "The window has also been maximized" }
    }
  }

  fun movedOrResized(source: InternalDecoratorImpl) {
    if (!source.isShowing) {
      // do not recalculate the tool window size if it is not yet shown (and, therefore, has 0,0,0,0 bounds)
      return
    }

    val toolWindow = source.toolWindow
    val info = manager.getRegisteredMutableInfoOrLogError(toolWindow.id)
    val externalDecorator = source.getExternalDecorator(info.type)
    val externalFloatingBounds = externalDecorator?.visibleWindowBounds
    if (info.type == ToolWindowType.FLOATING) {
      if (externalFloatingBounds != null) {
        info.floatingBounds = externalFloatingBounds
        LOG.debug { "Floating tool window ${toolWindow.id} bounds updated: ${info.floatingBounds}" }
      }
    }
    else if (info.type == ToolWindowType.WINDOWED) {
      val decorator = manager.idToEntry[toolWindow.id]?.windowedDecorator
      val frame = decorator?.getFrame()
      if (frame == null || !frame.isShowing) {
        return
      }
      info.floatingBounds = externalFloatingBounds
      info.isMaximized = (frame as JFrame).extendedState == Frame.MAXIMIZED_BOTH
      LOG.debug { "Windowed tool window ${toolWindow.id} bounds updated: ${info.floatingBounds}, maximized=${info.isMaximized}" }
    }
    else {
      // docked and sliding windows
      val dockingAreaComponent = if (source.parent is Splitter) source.parent as Splitter else source
      if (!dockingAreaComponentSizeCanBeTrusted(dockingAreaComponent)) {
        return
      }

      val anchor = info.anchor
      if (source.parent is Splitter) {
        var sizeInSplit = if (anchor.isSplitVertically) source.height else source.width
        val splitter = source.parent as Splitter
        if (splitter.secondComponent === source) {
          sizeInSplit += splitter.dividerWidth
        }
        info.sideWeight = ToolWindowManagerImpl.getAdjustedRatio(partSize = sizeInSplit,
                                                                 totalSize = if (anchor.isSplitVertically) splitter.height else splitter.width,
                                                                 direction = if (splitter.secondComponent === source) -1 else 1)
      }
      val toolWindowPane = manager.getToolWindowPane(toolWindow)
      val toolWindowWeight = getAdjustedWeight(toolWindowPane, anchor, source)
      val dockingAreaWeight = getAdjustedWeight(toolWindowPane, anchor, dockingAreaComponent)
      info.weight = toolWindowWeight
      manager.layoutState.setUnifiedAnchorWeight(anchor, dockingAreaWeight)
      LOG.debug {
        "Moved/resized tool window ${info.id}, updated weight=${toolWindowWeight}, docking area weight=${dockingAreaWeight}"
      }
    }
    manager.movedOrResizedStateChanged(toolWindow)
  }

  private fun removeDecoratorWithoutUpdatingState(entry: ToolWindowEntry, state: WindowInfoImpl, dirtyMode: Boolean) {
    removeExternalDecorators(entry)
    removeInternalDecorator(entry, state, dirtyMode, manager)
  }
}

// This is important for RD/CWM case, when we might want to keep the content 'showing' by attaching it to ShowingContainer.
private fun detachInternalDecorator(entry: ToolWindowEntry) {
  entry.toolWindow.decoratorComponent?.let { it.parent?.remove(it) }
}

private fun removeInternalDecorator(entry: ToolWindowEntry, state: WindowInfoImpl, dirtyMode: Boolean, manager: ToolWindowManagerImpl) {
  entry.toolWindow.decoratorComponent?.let {
    val toolWindowPane = manager.getToolWindowPane(state.safeToolWindowPaneId)
    toolWindowPane.removeDecorator(state, it, dirtyMode, manager)
    return
  }
}

private fun setExternalDecoratorBounds(
  info: WindowInfo,
  externalDecorator: ToolWindowExternalDecorator,
  internalDecorator: InternalDecoratorImpl,
  parentFrame: JFrame,
) {
  val storedBounds = info.floatingBounds
  val screen = ScreenUtil.getScreenRectangle(parentFrame)
  val needToCenter: Boolean
  val bounds: Rectangle
  if (storedBounds != null && isValidBounds(storedBounds)) {
    LOG.debug { "Keeping the tool window ${info.id} valid bounds: $storedBounds" }
    bounds = Rectangle(storedBounds)
    needToCenter = false
  }
  else if (storedBounds != null && storedBounds.width > 0 && storedBounds.height > 0) {
    LOG.debug { "Adjusting the stored bounds for the tool window ${info.id} to fit the screen $screen" }
    bounds = Rectangle(storedBounds)
    ScreenUtil.moveToFit(bounds, screen, null, true)
    LOG.debug { "Adjusted the stored bounds to fit the screen: $bounds" }
    needToCenter = true
  }
  else {
    LOG.debug { "Computing default bounds for the tool window ${info.id}" }
    // place a new frame at the center of the current frame if there are no floating bounds
    var size = internalDecorator.size
    if (size.width == 0 || size.height == 0) {
      val preferredSize = internalDecorator.preferredSize
      LOG.debug { "Using the preferred size $preferredSize because the size $size is invalid" }
      size = preferredSize
    }
    bounds = Rectangle(externalDecorator.visibleWindowBounds.location, size)
    LOG.debug { "Computed the bounds using the default location: $bounds" }
    needToCenter = true
  }
  externalDecorator.visibleWindowBounds = bounds
  if (needToCenter) {
    externalDecorator.setLocationRelativeTo(parentFrame)
    LOG.debug { "Centered the bounds relative to the IDE frame: ${externalDecorator.visibleWindowBounds}" }
  }
}

private fun isValidBounds(bounds: Rectangle): Boolean {
  val topLeftVisible = ScreenUtil.isVisible(bounds.topLeft)
  val topRightVisible = ScreenUtil.isVisible(bounds.topRight)
  val mostlyVisible = ScreenUtil.isVisible(bounds)
  val isValid = bounds.width > 0 && bounds.height > 0 &&
                (topLeftVisible || topRightVisible) && // At least some part of the header must be visible,
                mostlyVisible // and that some sensible portion of the window is better to be visible too.
  if (!isValid) {
    LOG.debug {
      "Not using saved bounds $bounds because they're invalid: " +
      "topLeftVisible=$topLeftVisible, topRightVisible=$topRightVisible mostlyVisible=$mostlyVisible"
    }
  }
  return isValid
}

private fun dockingAreaComponentSizeCanBeTrusted(dockingAreaComponent: Component): Boolean {
  val parentSplitter = dockingAreaComponent.parent as? ThreeComponentsSplitter
  if (parentSplitter == null) {
    // The window is not in a splitter (e.g., View Mode = Undock),
    // so we don't have to worry about the splitter resizing it in the wrong way, like in IDEA-319836.
    return true
  }
  val editorComponent = parentSplitter.innerComponent
  if (editorComponent == null) {
    LOG.info("Editor area is null, not updating tool window weights")
    return false
  }
  if (!editorComponent.isVisible) {
    LOG.info("Editor area is not visible, not updating tool window weights")
    return false
  }
  return true
}

private fun getAdjustedWeight(
  toolWindowPane: ToolWindowPane,
  anchor: ToolWindowAnchor,
  component: Component,
): Float {
  val wholeSize = toolWindowPane.rootPane.size
  return ToolWindowManagerImpl.getAdjustedRatio(
    partSize = if (anchor.isHorizontal) component.height else component.width,
    totalSize = if (anchor.isHorizontal) wholeSize.height else wholeSize.width,
    direction = 1,
  )
}

private val Rectangle.topLeft: Point
  get() = location
private val Rectangle.topRight: Point
  get() = Point(x + width, y)
