// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import java.awt.Frame
import java.awt.Rectangle
import javax.swing.JFrame

@Service(Service.Level.PROJECT)
internal class ProjectFrameBounds {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): ProjectFrameBounds = project.service<ProjectFrameBounds>()
  }

  internal val frameInfoHelper: FrameInfoHelper = FrameInfoHelper()
  private var pendingBounds: Rectangle? = null

  fun getActualFrameInfoInDeviceSpace(frameHelper: ProjectFrameHelper, frame: JFrame, windowManager: WindowManagerImpl): FrameInfo {
    return frameInfoHelper.updateAndGetInfo(frameHelper, frame, pendingBounds, windowManager)
  }

  fun markDirty(bounds: Rectangle?) {
    checkForNonsenseBounds("ProjectFrameBounds.markDirty.bounds", bounds)
    if (bounds != null) {
      pendingBounds = Rectangle(bounds)
    }
    frameInfoHelper.isDirty = true
  }
}

@Tag("frame")
@Property(style = Property.Style.ATTRIBUTE)
internal class FrameInfo : BaseState() {
  @get:Property(flat = true, style = Property.Style.ATTRIBUTE)
  var bounds: Rectangle? by property(null) { it == null || (it.width == 0 && it.height == 0 && it.x == 0 && it.y == 0) }

  var extendedState: Int by property(Frame.NORMAL)
  var fullScreen: Boolean by property(false)
}