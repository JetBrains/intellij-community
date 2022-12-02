// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.impl.FrameInfoHelper.Companion.isMaximized
import com.intellij.openapi.wm.impl.ProjectFrameHelper.Companion.getFrameHelper
import com.intellij.ui.BalloonLayout
import com.intellij.ui.mac.foundation.MacUtil
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import java.awt.Window
import java.util.*
import javax.accessibility.AccessibleContext
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JRootPane
import javax.swing.SwingUtilities

@ApiStatus.Internal
class IdeFrameImpl : JFrame(), IdeFrame, DataProvider {
  companion object {
    @JvmStatic
    val activeFrame: Window?
      get() = getFrames().firstOrNull { it.isActive }
  }

  var frameHelper: FrameHelper? = null
    private set

  var normalBounds: Rectangle? = null
  // when this client property is true, we have to ignore 'resizing' events and not spoil 'normal bounds' value for frame
  var togglingFullScreenInProgress: Boolean = true

  override fun getData(dataId: String): Any? = frameHelper?.getData(dataId)

  interface FrameHelper : DataProvider {
    val accessibleName: @Nls String?
    val project: Project?
    val helper: IdeFrame
    val frameDecorator: FrameDecorator?

    fun dispose()
  }

  interface FrameDecorator {
    val isInFullScreen: Boolean
    fun appClosing() {}
  }

  override fun createRootPane(): JRootPane? = null

  internal fun doSetRootPane(rootPane: JRootPane?) {
    super.setRootPane(rootPane)
    if (rootPane != null && isVisible && SystemInfoRt.isMac) {
      MacUtil.updateRootPane(this, rootPane)
    }
  }

  // NB!: the root pane must be set before decorator,
  // which holds its own client properties in a root pane
  fun setFrameHelper(frameHelper: FrameHelper?) {
    this.frameHelper = frameHelper
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (accessibleContext == null) {
      accessibleContext = AccessibleIdeFrameImpl()
    }
    return accessibleContext
  }

  override fun setExtendedState(state: Int) {
    // do not load FrameInfoHelper class
    if (LoadingState.COMPONENTS_REGISTERED.isOccurred && extendedState == NORMAL && isMaximized(state)) {
      normalBounds = bounds
    }
    super.setExtendedState(state)
  }

  override fun paint(g: Graphics) {
    if (LoadingState.COMPONENTS_REGISTERED.isOccurred) {
      setupAntialiasing(g)
    }
    super.paint(g)
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun show() {
    @Suppress("DEPRECATION")
    super.show()
    SwingUtilities.invokeLater { focusableWindowState = true }
  }

  override fun getInsets(): Insets {
    return if (SystemInfoRt.isMac && isInFullScreen) JBInsets.emptyInsets() else super.getInsets()
  }

  override fun isInFullScreen(): Boolean = frameHelper?.frameDecorator?.isInFullScreen ?: false

  override fun dispose() {
    val frameHelper = frameHelper
    if (frameHelper == null) {
      doDispose()
    }
    else {
      frameHelper.dispose()
    }
  }

  fun doDispose() {
    EdtInvocationManager.invokeLaterIfNeeded { super.dispose() }
  }

  private inner class AccessibleIdeFrameImpl : AccessibleJFrame() {
    override fun getAccessibleName(): String {
      val frameHelper = frameHelper
      return if (frameHelper == null) super.getAccessibleName() else frameHelper.accessibleName!!
    }
  }

  @Deprecated("Use {@link ProjectFrameHelper#getProject()} instead.", ReplaceWith("frameHelper?.project"))
  override fun getProject(): Project? = frameHelper?.project

  // deprecated stuff - as IdeFrame must be implemented (a lot of instanceof checks for JFrame)
  override fun getStatusBar(): StatusBar? = frameHelper?.helper?.statusBar

  override fun suggestChildFrameBounds(): Rectangle = frameHelper!!.helper.suggestChildFrameBounds()

  override fun setFrameTitle(title: String) {
    frameHelper?.helper?.setFrameTitle(title)
  }

  override fun getComponent(): JComponent = getRootPane()

  override fun getBalloonLayout(): BalloonLayout? = frameHelper?.helper?.balloonLayout

  override fun notifyProjectActivation() {
    getFrameHelper(this)?.notifyProjectActivation()
  }
}