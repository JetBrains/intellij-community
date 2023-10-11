// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.WindowInfo
import com.intellij.openapi.wm.impl.ToolWindowExternalDecorator.Companion.DECORATOR_PROPERTY
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.ClientProperty
import java.awt.Component
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent

internal class WindowedDecorator(
  project: Project,
  @NlsContexts.DialogTitle title: String,
  component: InternalDecoratorImpl,
) : FrameWrapper(project = project, title = title, component = component), ToolWindowExternalDecorator {

  init {
    val frame = getFrame()
    frame.addComponentListener(object : ComponentAdapter() {
      override fun componentMoved(e: ComponentEvent?) {
        if (LOG.isTraceEnabled) {
          LOG.trace(
            "Windowed tool window ${component.toolWindowId}" +
            " moved to ${frame.bounds}," +
            " scheduling bounds update"
          )
        }
        component.toolWindow.onMovedOrResized()
      }
      // resize is handled by the internal decorator
    })
    ClientProperty.put(frame, DECORATOR_PROPERTY, this)
  }

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
  }

  override var bounds: Rectangle
    get() = getFrame().bounds
    set(value) {
      getFrame().bounds = value
    }

}

private val LOG = logger<WindowedDecorator>()
