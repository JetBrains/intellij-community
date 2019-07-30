// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.impl.FrameInfoHelper.Companion.isFullScreenSupportedInCurrentOS
import com.intellij.openapi.wm.impl.WindowManagerImpl.FrameBoundsConverter.convertToDeviceSpace
import com.intellij.ui.FrameState
import com.intellij.ui.ScreenUtil
import sun.awt.AWTAccessor
import java.awt.Point
import java.awt.Rectangle
import java.awt.peer.FramePeer

class FrameInfoHelper {
  companion object {
    @JvmStatic
    fun isFullScreenSupportedInCurrentOS(): Boolean {
      return SystemInfo.isMacOSLion || SystemInfo.isWindows || (SystemInfo.isXWindow && X11UiUtil.isFullScreenSupported())
    }
  }

  // in device space
  var info: FrameInfo? = null
    private set

  @Volatile
  var isDirty = false

  fun setInfoInDeviceSpace(info: FrameInfo) {
    this.info = info
  }

  fun updateFrameInfo(frame: IdeFrameImpl) {
    info = updateFrameInfo(frame, null, info)
  }

  fun getModificationCount(): Long {
    return info?.modificationCount ?: 0
  }

  fun updateAndGetModificationCount(project: Project, lastNormalFrameBounds: Rectangle?, windowManager: WindowManagerImpl): Long {
    val frame = windowManager.getFrame(project) ?: return getModificationCount()
    return updateAndGetModificationCount(frame, lastNormalFrameBounds, windowManager)
  }

  fun updateAndGetModificationCount(frame: IdeFrameImpl, lastNormalFrameBounds: Rectangle?, windowManager: WindowManagerImpl): Long {
    val newInfo = updateFrameInfo(frame, lastNormalFrameBounds, info)
    updateDefaultFrameInfoInDeviceSpace(windowManager, newInfo)
    info = newInfo

    isDirty = false
    return getModificationCount()
  }

  fun copyFrom(newInfo: FrameInfo) {
    if (info == null) {
      info = FrameInfo()
    }
    info!!.copyFrom(newInfo)
    isDirty = false
  }
}

private fun updateFrameInfo(frame: IdeFrameImpl, lastNormalFrameBounds: Rectangle?, oldFrameInfo: FrameInfo?): FrameInfo {
  var extendedState = frame.extendedState
  if (SystemInfo.isMacOSLion) {
    val peer = AWTAccessor.getComponentAccessor().getPeer(frame)
    if (peer is FramePeer) {
      // frame.state is not updated by jdk so get it directly from peer
      extendedState = peer.state
    }
  }

  val isInFullScreen = isFullScreenSupportedInCurrentOS() && frame.isInFullScreen
  val isMaximized = FrameState.isMaximized(extendedState) || isInFullScreen

  val oldBounds = oldFrameInfo?.bounds
  val newBounds = convertToDeviceSpace(frame.graphicsConfiguration, lastNormalFrameBounds ?: frame.bounds)

  val usePreviousBounds = lastNormalFrameBounds == null && isMaximized &&
                          oldBounds != null &&
                          newBounds.contains(Point(oldBounds.centerX.toInt(), oldBounds.centerY.toInt()))

  // don't report if was already reported
  if (!usePreviousBounds && oldBounds != newBounds && !ScreenUtil.intersectsVisibleScreen(frame)) {
    logger<WindowInfoImpl>().error("Frame bounds are invalid: $newBounds")
  }

  val frameInfo = oldFrameInfo ?: FrameInfo()
  if (!usePreviousBounds) {
    frameInfo.bounds = newBounds
  }
  frameInfo.extendedState = extendedState
  if (isFullScreenSupportedInCurrentOS()) {
    frameInfo.fullScreen = isInFullScreen
  }
  frameInfo.bounds = newBounds
  return frameInfo
}

internal fun updateDefaultFrameInfoInDeviceSpace(windowManager: WindowManagerImpl, newInfo: FrameInfo) {
  // see comment in the myFrameStateListener about chicken and egg problem
  windowManager.defaultFrameInfoHelper.copyFrom(newInfo)
}