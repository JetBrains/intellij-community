// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.FrameWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.WindowInfo
import com.intellij.openapi.wm.impl.ToolWindowExternalDecorator.Companion.DECORATOR_PROPERTY
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.ClientProperty
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JFrame

internal class WindowedDecorator(
  project: Project,
  @NlsContexts.DialogTitle title: String,
  component: InternalDecoratorImpl,
) : FrameWrapper(project = project, title = title, component = component), ToolWindowExternalDecorator {

  init {
    val frame = getFrame()
    frame.addComponentListener(object : ComponentAdapter() {
      override fun componentMoved(e: ComponentEvent?) {
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
      getFrame().bounds = bounds
    }
  }

}
