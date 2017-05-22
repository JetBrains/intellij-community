/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm.impl

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.WindowManagerImpl.FrameBoundsConverter.convertToDeviceSpace
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import org.jdom.Element
import java.awt.Frame
import java.awt.Rectangle

@State(name = "ProjectFrameBounds", storages = arrayOf(Storage(StoragePathMacros.WORKSPACE_FILE)))
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
    val windowManager = WindowManager.getInstance() as WindowManagerImpl
    val frameInfoInDeviceSpace = windowManager.getFrameInfoInDeviceSpace(project)
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
  @get:Property(flat = true) var bounds by storedProperty<Rectangle>()
  @get:Attribute var extendedState by storedProperty(Frame.NORMAL)

  @get:Attribute var fullScreen by storedProperty(false)
}

fun WindowManagerImpl.getFrameInfoInDeviceSpace(project: Project): FrameInfo? {
  val frame = getFrame(project) ?: return null

  // updateFrameBounds will also update myDefaultFrameInfo,
  // so, we have to call this method before other code in this method and if later extendedState is used only for macOS
  val extendedState = updateFrameBounds(frame)
  val frameInfo = FrameInfo()
  // save bounds even if maximized because on unmaximize we must restore previous frame bounds
  frameInfo.bounds = convertToDeviceSpace(frame.graphicsConfiguration, myDefaultFrameInfo.bounds!!)
  if (!(frame.isInFullScreen && SystemInfo.isAppleJvm)) {
    frameInfo.extendedState = extendedState
  }

  if (isFullScreenSupportedInCurrentOS) {
    frameInfo.fullScreen = frame.isInFullScreen
  }
  return frameInfo
}

private val X_ATTR = "x"
private val Y_ATTR = "y"
private val WIDTH_ATTR = "width"
private val HEIGHT_ATTR = "height"

fun serializeBounds(bounds: Rectangle, element: Element) {
  element.setAttribute(X_ATTR, Integer.toString(bounds.x))
  element.setAttribute(Y_ATTR, Integer.toString(bounds.y))
  element.setAttribute(WIDTH_ATTR, Integer.toString(bounds.width))
  element.setAttribute(HEIGHT_ATTR, Integer.toString(bounds.height))
}

fun deserializeBounds(element: Element): Rectangle? {
  try {
    return Rectangle(
      element.getAttributeValue(X_ATTR).toInt(), element.getAttributeValue(Y_ATTR).toInt(),
      element.getAttributeValue(WIDTH_ATTR).toInt(), element.getAttributeValue(HEIGHT_ATTR).toInt())
  }
  catch (ignored: NumberFormatException) {
    return null
  }
}