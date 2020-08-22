// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.openapi.wm.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.impl.FrameBoundsConverter.convertToDeviceSpace
import com.intellij.openapi.wm.impl.FrameInfoHelper.Companion.isFullScreenSupportedInCurrentOs
import com.intellij.ui.ScreenUtil
import sun.awt.AWTAccessor
import java.awt.Frame
import java.awt.Point
import java.awt.Rectangle
import java.awt.peer.ComponentPeer
import java.awt.peer.FramePeer

internal class FrameInfoHelper {
  companion object {
    @JvmStatic
    fun isFullScreenSupportedInCurrentOs(): Boolean {
      return SystemInfo.isMac || SystemInfo.isWindows || (SystemInfo.isXWindow && X11UiUtil.isFullScreenSupported())
    }

    @JvmStatic
    val isFloatingMenuBarSupported: Boolean
      get() = !SystemInfo.isMac && isFullScreenSupportedInCurrentOs()

    @JvmStatic
    fun isMaximized(state: Int): Boolean {
      return state and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH
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

  fun updateFrameInfo(frame: ProjectFrameHelper) {
    info = updateFrameInfo(frame, null, info)
  }

  fun getModificationCount(): Long {
    return info?.modificationCount ?: 0
  }

  fun updateAndGetModificationCount(project: Project, lastNormalFrameBounds: Rectangle?, windowManager: WindowManagerImpl): Long {
    val frame = windowManager.getFrameHelper(project) ?: return getModificationCount()
    return updateAndGetModificationCount(frame, lastNormalFrameBounds, windowManager)
  }

  fun updateAndGetModificationCount(frame: ProjectFrameHelper, lastNormalFrameBounds: Rectangle?, windowManager: WindowManagerImpl): Long {
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

private fun updateFrameInfo(frameHelper: ProjectFrameHelper, lastNormalFrameBounds: Rectangle?, oldFrameInfo: FrameInfo?): FrameInfo {
  val frame = frameHelper.frame
  var extendedState = frame.extendedState
  if (SystemInfo.isMac) {
    // java 11
    @Suppress("USELESS_CAST")
    val peer = AWTAccessor.getComponentAccessor().getPeer(frame) as ComponentPeer?
    if (peer is FramePeer) {
      // frame.state is not updated by jdk so get it directly from peer
      extendedState = peer.state
    }
  }

  val isInFullScreen = isFullScreenSupportedInCurrentOs() && frameHelper.isInFullScreen
  val isMaximized = FrameInfoHelper.isMaximized(extendedState) || isInFullScreen

  val oldBounds = oldFrameInfo?.bounds
  val newBounds = convertToDeviceSpace(frame.graphicsConfiguration, if (isMaximized && lastNormalFrameBounds != null) lastNormalFrameBounds else frame.bounds)

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
  if (isFullScreenSupportedInCurrentOs()) {
    frameInfo.fullScreen = isInFullScreen
  }
  return frameInfo
}

internal fun updateDefaultFrameInfoInDeviceSpace(windowManager: WindowManagerImpl, newInfo: FrameInfo) {
  // see comment in the myFrameStateListener about chicken and egg problem
  windowManager.defaultFrameInfoHelper.copyFrom(newInfo)
}