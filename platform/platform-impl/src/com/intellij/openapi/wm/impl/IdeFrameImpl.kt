// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.impl.FrameInfoHelper.Companion.isMaximized
import com.intellij.openapi.wm.impl.ProjectFrameHelper.Companion.getFrameHelper
import com.intellij.ui.BalloonLayout
import com.intellij.ui.mac.foundation.MacUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
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

  private var linuxFullScreenSynchronizer: LinuxFullScreenSynchronizer? = null

  init {
    if (SystemInfoRt.isXWindow) {
      linuxFullScreenSynchronizer = LinuxFullScreenSynchronizer(this)
    }

    if (IDE_FRAME_EVENT_LOG.isDebugEnabled) {
      addComponentListener(EventLogger(frame = this, log = IDE_FRAME_EVENT_LOG))
    }
  }

  var frameHelper: FrameHelper? = null
    private set

  var reusedFullScreenState: Boolean = false

  var normalBounds: Rectangle? = null
  var screenBounds: Rectangle? = null

  // when this client property is true, we have to ignore 'resizing' events and not spoil 'normal bounds' value for frame
  @JvmField
  internal var togglingFullScreenInProgress: Boolean = false

  override fun getData(dataId: String): Any? = frameHelper?.getData(dataId)

  interface FrameHelper : DataProvider {
    val accessibleName: @Nls String?
    val project: Project?
    val helper: IdeFrame
    val isInFullScreen: Boolean?

    fun dispose()
  }

  internal fun doSetRootPane(rootPane: JRootPane?) {
    val oldRootPane = this.rootPane
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
    val maximized = isMaximized(state)

    // do not load FrameInfoHelper class
    if (LoadingState.COMPONENTS_REGISTERED.isOccurred && extendedState == NORMAL && maximized) {
      normalBounds = bounds
      screenBounds = graphicsConfiguration?.bounds
      if (IDE_FRAME_EVENT_LOG.isDebugEnabled) { // avoid unnecessary concatenation
        IDE_FRAME_EVENT_LOG.debug("Saved bounds for IDE frame ${normalBounds} and screen ${screenBounds} before maximizing")
      }
    }

    if (maximized && SystemInfoRt.isXWindow && X11UiUtil.isInitialized()) {
      // Ubuntu (and may be other linux distros) doesn't set maximized correctly if the frame is MAXIMIZED_VERT already. Use X11 API
      X11UiUtil.setMaximized(this, true)
    } else {
      super.setExtendedState(state)
    }
  }

  override fun paint(g: Graphics) {
    if (LoadingState.COMPONENTS_REGISTERED.isOccurred) {
      setupAntialiasing(g)
    }
    super.paint(g)
  }

  @Suppress("OVERRIDE_DEPRECATION")
  override fun show() {
    if (IdeRootPane.hideNativeLinuxTitle && !isUndecorated) {
      isUndecorated = true
    }
    @Suppress("DEPRECATION")
    super.show()
    SwingUtilities.invokeLater { focusableWindowState = true }
  }

  override fun getInsets(): Insets {
    return if (SystemInfoRt.isMac && isInFullScreen) JBInsets.emptyInsets() else super.getInsets()
  }

  override fun isInFullScreen(): Boolean = frameHelper?.isInFullScreen ?: false

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
    EdtInvocationManager.invokeLaterIfNeeded {
      // must be called in addition to the `dispose`, otherwise not removed from `Window.allWindows` list.
      isVisible = false

      linuxFullScreenSynchronizer?.let {
        it.dispose()
        linuxFullScreenSynchronizer = null
      }

      super.dispose()
    }
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
    this.title = title
  }

  override fun getComponent(): JComponent = getRootPane()

  override fun getBalloonLayout(): BalloonLayout? = frameHelper?.helper?.balloonLayout

  override fun notifyProjectActivation() {
    getFrameHelper(this)?.notifyProjectActivation()
  }
}

private class EventLogger(private val frame: IdeFrameImpl, private val log: Logger) : ComponentAdapter() {
  companion object {
    private fun toDebugString(rectangle: Rectangle): String {
      return "${rectangle.width}x${rectangle.height} @ (${rectangle.x},${rectangle.y})"
    }
  }

  override fun componentResized(e: ComponentEvent) {
    logBounds("resized")
  }

  override fun componentMoved(e: ComponentEvent) {
    logBounds("moved")
  }

  private fun logBounds(action: String) {
    val windowBounds = frame.bounds
    val gc = frame.graphicsConfiguration ?: return
    val mode = gc.device?.displayMode ?: return
    val scale = JBUIScale.sysScale(gc)
    val screenBounds = gc.bounds
    log.debug(
      "IDE frame '${frame.frameHelper?.project?.name}' $action; " +
      "frame bounds: ${toDebugString(windowBounds)}; " +
      "resolution: ${mode.width}x${mode.height}; " +
      "scale: $scale; " +
      "screen bounds: ${toDebugString(screenBounds)}"
    )
  }
}

/**
 * Linux unexpectedly turns on FullScreen for undecorated maximized frame on secondary monitor (without taskbar and other system widgets).
 * For example, it happens with some delay in the following cases:
 * * frame moved from one monitor to a secondary monitor via keyboard shortcuts
 * * IDE is started on secondary monitor
 */
private class LinuxFullScreenSynchronizer(private val frame: IdeFrameImpl) {

  companion object {
    val LOG = logger<LinuxFullScreenSynchronizer>()
    const val DELAY_MS = 500
  }

  private val alarm = Alarm()

  init {
    alarm.addRequest(::syncFullScreen, DELAY_MS)
  }

  fun dispose() {
    Disposer.dispose(alarm)
  }

  private fun syncFullScreen() {
    if (frame.isShowing && !frame.isInFullScreen && X11UiUtil.isInFullScreenMode(frame)) {
      LOG.info("Looks like Linux unexpectedly turned on FullScreen mode. Resetting it")
      X11UiUtil.setFullScreenMode(frame, false)
    }
    alarm.addRequest(::syncFullScreen, DELAY_MS)
  }
}
