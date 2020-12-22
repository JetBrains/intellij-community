// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import java.awt.Frame
import java.awt.Rectangle

@Service
class ProjectFrameBounds(private val project: Project) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<ProjectFrameBounds>()
  }

  internal val frameInfoHelper = FrameInfoHelper()
  private var pendingBounds: Rectangle? = null

  /**
   * Maybe outdated. You must always check isDirty if you need actual info.
   */
  internal fun getFrameInfoInDeviceSpace() = frameInfoHelper.info

  internal fun getActualFrameInfoInDeviceSpace(frame: ProjectFrameHelper, windowManager: WindowManagerImpl): FrameInfo {
    val result = frameInfoHelper.info
    if (frameInfoHelper.isDirty || result == null) {
      frameInfoHelper.updateAndGetModificationCount(frame, pendingBounds, windowManager)
      return frameInfoHelper.info!!
    }
    return result
  }

  fun updateDefaultFrameInfoOnProjectClose() {
    val windowManager = WindowManager.getInstance() as WindowManagerImpl
    if (frameInfoHelper.isDirty || frameInfoHelper.info == null) {
      // not really required, because will be applied during project save on close
      frameInfoHelper.updateAndGetModificationCount(project, pendingBounds, windowManager)
    }
    else {
      updateDefaultFrameInfoInDeviceSpace(windowManager, frameInfoHelper.info ?: return)
    }
  }

  fun markDirty(bounds: Rectangle?) {
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
  var bounds by property<Rectangle?>(null) { it == null || (it.width == 0 && it.height == 0 && it.x == 0 && it.y == 0) }

  var extendedState by property(Frame.NORMAL)
  var fullScreen by property(false)
}