// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")
package com.intellij.openapi.wm.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.impl.FrameBoundsConverter.convertToDeviceSpace
import com.intellij.openapi.wm.impl.FrameInfoHelper.Companion.isFullScreenSupportedInCurrentOs
import com.intellij.ui.ScreenUtil
import com.intellij.ui.scale.JBUIScale
import sun.awt.AWTAccessor
import java.awt.Frame
import java.awt.Point
import java.awt.Rectangle
import java.awt.peer.ComponentPeer
import java.awt.peer.FramePeer
import javax.swing.JFrame

internal class FrameInfoHelper {
  companion object {
    @JvmStatic
    fun isFullScreenSupportedInCurrentOs(): Boolean {
      return SystemInfoRt.isMac || SystemInfoRt.isWindows || (SystemInfoRt.isXWindow && X11UiUtil.isFullScreenSupported())
    }

    val isFloatingMenuBarSupported: Boolean
      get() = !SystemInfoRt.isMac && isFullScreenSupportedInCurrentOs()

    @JvmStatic
    fun isMaximized(state: Int): Boolean {
      return state and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH
    }
  }

  // in device space
  var info: FrameInfo? = null
    private set

  @Volatile
  var isDirty: Boolean = false

  fun updateFrameInfo(frameHelper: ProjectFrameHelper, frame: JFrame) {
    info = updateFrameInfo(frameHelper, frame, null, info)
  }

  fun getModificationCount(): Long {
    return info?.modificationCount ?: 0
  }

  fun update(project: Project, lastNormalFrameBounds: Rectangle?, windowManager: WindowManagerImpl) {
    val frameHelper = windowManager.getFrameHelper(project) ?: return
    updateAndGetInfo(frameHelper, frameHelper.frame, lastNormalFrameBounds, windowManager)
  }

  fun updateAndGetInfo(frameHelper: ProjectFrameHelper,
                       frame: JFrame,
                       lastNormalFrameBounds: Rectangle?,
                       windowManager: WindowManagerImpl): FrameInfo {
    val newInfo = updateFrameInfo(frameHelper, frame, lastNormalFrameBounds, info)
    windowManager.defaultFrameInfoHelper.copyFrom(newInfo)
    info = newInfo
    isDirty = false
    return newInfo
  }

  fun copyFrom(newInfo: FrameInfo) {
    if (info == null) {
      info = FrameInfo()
    }
    info!!.copyFrom(newInfo)
    isDirty = false
  }
}

internal fun updateFrameInfo(frameHelper: ProjectFrameHelper, frame: JFrame, lastNormalFrameBounds: Rectangle?, oldFrameInfo: FrameInfo?): FrameInfo {
  var extendedState = frame.extendedState
  if (SystemInfoRt.isMac) {
    // java 11
    val peer = AWTAccessor.getComponentAccessor().getPeer(frame) as ComponentPeer?
    if (peer is FramePeer) {
      // frame.state is not updated by jdk so get it directly from peer
      extendedState = peer.state
    }
  }

  val isInFullScreen = isFullScreenSupportedInCurrentOs() && frameHelper.isInFullScreen
  val isMaximized = FrameInfoHelper.isMaximized(extendedState) || isInFullScreen

  val oldBounds = oldFrameInfo?.bounds
  val newBounds = convertToDeviceSpace(frame.graphicsConfiguration,
                                       if (isMaximized && lastNormalFrameBounds != null) lastNormalFrameBounds else frame.bounds)

  val usePreviousBounds = lastNormalFrameBounds == null && isMaximized &&
                          oldBounds != null &&
                          newBounds.contains(Point(oldBounds.centerX.toInt(), oldBounds.centerY.toInt()))

  if (IDE_FRAME_EVENT_LOG.isDebugEnabled) { // avoid unnecessary concatenation
    IDE_FRAME_EVENT_LOG.debug(
      "Updating frame bounds: lastNormalFrameBounds = $lastNormalFrameBounds, " +
      "frame.bounds = ${frame.bounds}, " +
      "frame screen = ${frame.graphicsConfiguration.bounds}, scale = ${JBUIScale.sysScale(frame.graphicsConfiguration)}, " +
      "isMaximized = $isMaximized, " +
      "isInFullScreen = $isInFullScreen, " +
      "oldBounds = $oldBounds, " +
      "newBounds = $newBounds, " +
      "usePreviousBounds = $usePreviousBounds"
    )
  }

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