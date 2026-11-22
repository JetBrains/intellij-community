// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.WindowInfo
import com.intellij.openapi.wm.impl.ToolWindowExternalDecorator.Companion.DECORATOR_PROPERTY
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.util.coroutines.childScope
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.toolWindow.ToolWindowHeader
import com.intellij.ui.ClientProperty
import com.intellij.ui.mac.MacTitleBarContent
import com.intellij.ui.mac.MacTitleBarGeometry
import com.intellij.ui.mac.MacWindowTitleBar
import com.intellij.util.ui.JBInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.awt.Component
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyListener
import javax.swing.SwingUtilities
import javax.swing.border.Border
import javax.swing.border.EmptyBorder
import kotlin.math.max

internal class WindowedDecorator(
  private val project: Project,
  @NlsContexts.DialogTitle title: String,
  private val decorator: InternalDecoratorImpl,
) : FrameWrapper(project = project, title = title, component = decorator), ToolWindowExternalDecorator {

  private val boundsHelper = ToolWindowExternalDecoratorBoundsHelper(this)

  override val id = decorator.toolWindowId

  init {
    val frame = getFrame()
    frame.addComponentListener(object : ComponentAdapter() {
      override fun componentMoved(e: ComponentEvent?) {
        if (LOG.isTraceEnabled) {
          LOG.trace(
            "Windowed tool window $id" +
            " moved to ${frame.bounds}," +
            " scheduling bounds update"
          )
        }
        decorator.toolWindow.onMovedOrResized()
      }
      // resize is handled by the internal decorator
    })
    ClientProperty.put(frame, DECORATOR_PROPERTY, this)
  }

  override val window: Window
    get() = getFrame()

  override fun getToolWindowType() = ToolWindowType.WINDOWED

  override fun apply(info: WindowInfo) {
    val bounds = info.floatingBounds
    if (bounds != null) {
      if (LOG.isDebugEnabled) {
        LOG.debug("Applying windowed tool window ${info.id} bounds $bounds")
      }
      getFrame().bounds = bounds
    }
    else {
      if (LOG.isDebugEnabled) {
        LOG.debug("Windowed tool window ${info.id} info has no bounds, not applying anything")
      }
    }
  }

  override fun setLocationRelativeTo(parentFrame: Component?) {
    getFrame().setLocationRelativeTo(parentFrame)
    boundsHelper.bounds = getFrame().bounds
  }

  override var visibleWindowBounds: Rectangle
    get() {
      val result = getFrame().bounds
      JBInsets.removeFrom(result, getFrame().invisibleInsets)
      return result
    }
    set(value) {
      boundsHelper.bounds = value
      val newBounds = Rectangle(value)
      JBInsets.addTo(newBounds, getFrame().invisibleInsets)
      getFrame().bounds = newBounds
    }

  override fun log(): Logger = LOG

  /**
   * Shows the tool window header in the title bar row of macOS while the header is visible and the tool window is not split.
   * Otherwise, the frame shows the system title bar row. Call it after the frame shows.
   */
  fun mergeHeaderIntoTitleBar() {
    val titleBar = macTitleBar ?: return
    if (titleBar.canMerge) {
      WindowedHeaderInTitleBar(decorator = decorator, titleBar = titleBar, project = project, parentDisposable = this)
    }
  }
}

/**
 * Returns the content of the title bar row for [decorator], or null when the frame keeps the system title bar row.
 */
internal fun windowedTitleBarContent(decorator: InternalDecoratorImpl): MacTitleBarContent? {
  val header = decorator.header
  if (decorator.mode != InternalDecoratorImpl.Mode.SINGLE || !header.isVisible) {
    return null
  }

  return object : MacTitleBarContent {
    override val height: Int
      get() = SwingUtilities.convertPoint(header, 0, header.height, layeredPaneOf(header)).y

    override fun isTitleBarArea(component: Component): Boolean = header.isTitleBarArea(component)
  }
}

private fun layeredPaneOf(component: Component): Component? = SwingUtilities.getRootPane(component)?.layeredPane

private class WindowedHeaderInTitleBar(
  private val decorator: InternalDecoratorImpl,
  private val titleBar: MacWindowTitleBar,
  project: Project,
  parentDisposable: Disposable,
) {
  private val header = decorator.header
  private val systemBorder: Border? = header.border
  private var applied: MacTitleBarGeometry? = null

  init {
    val componentListener = object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) = update()
      override fun componentMoved(e: ComponentEvent) = update()
      override fun componentShown(e: ComponentEvent) = update()
      override fun componentHidden(e: ComponentEvent) = update()
    }
    header.addComponentListener(componentListener)
    val hierarchyListener = HierarchyListener { update() }
    header.addHierarchyListener(hierarchyListener)

    val coroutineScope = project.service<CoreUiCoroutineScopeHolder>().coroutineScope.childScope("Windowed tool window title bar")
    coroutineScope.launch(Dispatchers.UI + ModalityState.any().asContextElement()) {
      titleBar.merged.collect(::apply)
    }

    Disposer.register(parentDisposable) {
      coroutineScope.cancel()
      header.removeComponentListener(componentListener)
      header.removeHierarchyListener(hierarchyListener)
      // the tool window reuses the decorator when it leaves the window mode
      apply(null)
    }
    update()
  }

  private fun update() {
    titleBar.merge(windowedTitleBarContent(decorator))
    apply(titleBar.merged.value)
  }

  private fun apply(geometry: MacTitleBarGeometry?) {
    if (geometry == applied) {
      return
    }

    applied = geometry
    if (geometry == null) {
      ClientProperty.put(header, ToolWindowHeader.IN_MAC_TITLE_BAR, null)
      header.border = systemBorder
    }
    else {
      ClientProperty.put(header, ToolWindowHeader.IN_MAC_TITLE_BAR, true)
      // in the Islands theme, the header starts after the gap of its island
      val headerX = layeredPaneOf(header)?.let { SwingUtilities.convertPoint(header, 0, 0, it).x } ?: 0
      @Suppress("UseDPIAwareBorders")
      header.border = EmptyBorder(0, max(0, geometry.leadingInset - headerX), 0, 0)
    }
    header.revalidate()
    header.repaint()
  }
}

private val LOG = logger<WindowedDecorator>()
