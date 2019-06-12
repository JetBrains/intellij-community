// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.WindowManagerImpl.FrameBoundsConverter.convertToDeviceSpace
import com.intellij.ui.ScreenUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import org.jdom.Element
import java.awt.Frame
import java.awt.Rectangle

@State(name = "ProjectFrameBounds", storages = [
  Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE),
  Storage(StoragePathMacros.WORKSPACE_FILE, deprecated = true)
])
class ProjectFrameBounds(private val project: Project) : PersistentStateComponent<FrameInfo>, ModificationTracker {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<ProjectFrameBounds>()
  }

  // in device space
  var rawFrameInfo: FrameInfo? = null
    private set

  val isInFullScreen: Boolean
    get() = rawFrameInfo?.fullScreen ?: false

  override fun getState() = rawFrameInfo

  override fun loadState(state: FrameInfo) {
    rawFrameInfo = state
    state.resetModificationCount()
  }

  override fun getModificationCount(): Long {
    val frameInfoInDeviceSpace = (WindowManager.getInstance() as? WindowManagerImpl)?.let { getFrameInfoInDeviceSpace(it, project, rawFrameInfo) }
    if (frameInfoInDeviceSpace != null) {
      if (rawFrameInfo == null) {
        rawFrameInfo = frameInfoInDeviceSpace
      }
      else {
        rawFrameInfo!!.copyFrom(frameInfoInDeviceSpace)
      }
    }
    return rawFrameInfo?.modificationCount ?: 0
  }
}

class FrameInfo : BaseState() {
  // flat is used due to backward compatibility
  @get:Property(flat = true) var bounds by property<Rectangle?>(null) { it == null || (it.width == 0 && it.height == 0 && it.x == 0 && it.y == 0) }
  @get:Attribute var extendedState by property(Frame.NORMAL)

  @get:Attribute var fullScreen by property(false)
}

internal fun getFrameInfoInDeviceSpace(windowManagerImpl: WindowManagerImpl, project: Project, prev: FrameInfo?): FrameInfo? {
  val frame = windowManagerImpl.getFrame(project) ?: return null

  // updateFrameBounds will also update myDefaultFrameInfo,
  // so, we have to call this method before other code in this method and if later extendedState is used only for macOS
  val extendedState = windowManagerImpl.updateFrameBounds(frame)

  // save bounds even if maximized because on unmaximize we must restore previous frame bounds
  val deviceSpaceBounds = convertToDeviceSpace(frame.graphicsConfiguration, windowManagerImpl.myDefaultFrameInfo.bounds ?: return null)

  // don't report if was already reported
  if (prev?.bounds != deviceSpaceBounds && !ScreenUtil.intersectsVisibleScreen(frame)) {
    logger<WindowInfoImpl>().error("Frame bounds are invalid: $deviceSpaceBounds")
  }

  val frameInfo = FrameInfo()
  frameInfo.bounds = deviceSpaceBounds
  frameInfo.extendedState = extendedState
  if (windowManagerImpl.isFullScreenSupportedInCurrentOS) {
    frameInfo.fullScreen = frame.isInFullScreen
  }
  return frameInfo
}

private const val X_ATTR = "x"
private const val Y_ATTR = "y"
private const val WIDTH_ATTR = "width"
private const val HEIGHT_ATTR = "height"

fun serializeBounds(bounds: Rectangle, element: Element) {
  element.setAttribute(X_ATTR, Integer.toString(bounds.x))
  element.setAttribute(Y_ATTR, Integer.toString(bounds.y))
  element.setAttribute(WIDTH_ATTR, Integer.toString(bounds.width))
  element.setAttribute(HEIGHT_ATTR, Integer.toString(bounds.height))
}

fun deserializeBounds(element: Element): Rectangle? {
  try {
    val x = element.getAttributeValue(X_ATTR)?.toInt() ?: return null
    val y = element.getAttributeValue(Y_ATTR)?.toInt() ?: return null
    val w = element.getAttributeValue(WIDTH_ATTR)?.toInt() ?: return null
    val h = element.getAttributeValue(HEIGHT_ATTR)?.toInt() ?: return null
    return Rectangle(x, y, w, h)
  }
  catch (ignored: NumberFormatException) {
    return null
  }
}