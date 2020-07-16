// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import java.awt.Frame
import java.awt.Rectangle

@State(name = "ProjectFrameBounds", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
class ProjectFrameBounds(private val project: Project) : PersistentStateComponent<FrameInfo>, ModificationTracker {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<ProjectFrameBounds>()
  }

  private val frameInfoHelper = FrameInfoHelper()
  private var pendingBounds: Rectangle? = null

  val isInFullScreen: Boolean
    get() = frameInfoHelper.info?.fullScreen ?: false

  override fun getState() = frameInfoHelper.info

  override fun loadState(state: FrameInfo) {
    frameInfoHelper.setInfoInDeviceSpace(state)
  }

  override fun getModificationCount(): Long {
    return when {
      frameInfoHelper.isDirty -> frameInfoHelper.updateAndGetModificationCount(project, pendingBounds, WindowManager.getInstance() as WindowManagerImpl)
      else -> frameInfoHelper.getModificationCount()
    }
  }

  /**
   * Maybe outdated. You must always check isDirty if you need actual info.
   */
  fun getFrameInfoInDeviceSpace() = frameInfoHelper.info

  fun getActualFrameInfoInDeviceSpace(frame: ProjectFrameHelper, windowManager: WindowManagerImpl): FrameInfo? {
    if (frameInfoHelper.isDirty) {
      updateAndGetModificationCount(frame, windowManager)
    }
    return frameInfoHelper.info
  }

  private fun updateAndGetModificationCount(frame: ProjectFrameHelper, windowManager: WindowManagerImpl) {
    frameInfoHelper.updateAndGetModificationCount(frame, pendingBounds, windowManager)
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
class FrameInfo : BaseState() {
  @get:Property(flat = true, style = Property.Style.ATTRIBUTE)
  var bounds by property<Rectangle?>(null) { it == null || (it.width == 0 && it.height == 0 && it.x == 0 && it.y == 0) }

  var extendedState by property(Frame.NORMAL)
  var fullScreen by property(false)
}