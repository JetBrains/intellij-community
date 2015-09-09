/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.reactiveidea

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.WindowManagerListener
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.CommandProcessor
import com.intellij.openapi.wm.impl.DesktopLayout
import com.intellij.openapi.wm.impl.IdeFrameImpl
import java.awt.*
import java.awt.event.ComponentEvent
import javax.swing.JDialog
import javax.swing.JFrame

public class ServerWindowManager : WindowManagerEx() {
  override fun isAlphaModeSupported(): Boolean = false

  override fun setAlphaModeRatio(window: Window?, ratio: Float) {

  }

  override fun isAlphaModeEnabled(window: Window?): Boolean = false

  override fun setAlphaModeEnabled(window: Window?, state: Boolean) {

  }

  override fun doNotSuggestAsParent(window: Window?) {

  }

  override fun suggestParentWindow(project: Project?): Window? = null

  override fun getStatusBar(project: Project?): StatusBar? = null

  override fun getStatusBar(c: Component): StatusBar? = null

  override fun getIdeFrame(project: Project?): IdeFrame? = null

  override fun isInsideScreenBounds(x: Int, y: Int, width: Int): Boolean = false

  override fun isInsideScreenBounds(x: Int, y: Int): Boolean = false

  override fun getAllProjectFrames(): Array<out IdeFrame> = arrayOf()

  override fun findVisibleFrame(): JFrame? = null

  override fun addListener(listener: WindowManagerListener?) {

  }

  override fun removeListener(listener: WindowManagerListener?) {

  }

  override fun isFullScreenSupportedInCurrentOS(): Boolean = false

  override fun getFrame(project: Project?): IdeFrameImpl? = null

  override fun allocateFrame(project: Project?): IdeFrameImpl? = null

  override fun releaseFrame(frame: IdeFrameImpl?) {

  }

  override fun getFocusedComponent(window: Window): Component? = null

  override fun getFocusedComponent(project: Project?): Component? = null

  override fun getMostRecentFocusedWindow(): Window? = null

  override fun findFrameFor(project: Project?): IdeFrame? = null

  override fun getLayout(): DesktopLayout? = null

  override fun setLayout(layout: DesktopLayout?) {

  }

  override fun dispatchComponentEvent(e: ComponentEvent?) {

  }

  override fun getScreenBounds(): Rectangle? = null

  override fun getScreenBounds(project: Project): Rectangle? = null

  override fun setWindowMask(window: Window?, mask: Shape?) {

  }

  override fun setWindowShadow(window: Window?, mode: WindowManagerEx.WindowShadowMode?) {

  }

  override fun resetWindow(window: Window?) {

  }

  override fun hideDialog(dialog: JDialog?, project: Project?) {

  }

  override fun adjustContainerWindow(c: Component?, oldSize: Dimension?, newSize: Dimension?) {

  }

}