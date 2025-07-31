// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.traceThrowable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.impl.FrameInfoHelper.Companion.isMaximized
import com.intellij.openapi.wm.impl.ProjectFrameHelper.Companion.getFrameHelper
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil.hideNativeLinuxTitle
import com.intellij.platform.ide.diagnostic.startUpPerformanceReporter.FUSProjectHotStartUpMeasurer
import com.intellij.ui.BalloonLayout
import com.intellij.ui.DisposableWindow
import com.intellij.ui.ScreenUtil
import com.intellij.ui.mac.foundation.MacUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import javax.accessibility.AccessibleContext
import javax.swing.*
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
class IdeFrameImpl : JFrame(), IdeFrame, UiDataProvider, DisposableWindow {
  companion object {
    @JvmStatic
    val activeFrame: Window?
      get() = getFrames().firstOrNull { it.isActive }
  }

  private val mouseActivationWatcher = object : IdeEventQueue.NonLockedEventDispatcher, Disposable {
    override fun dispatch(e: AWTEvent): Boolean {
      detectWindowActivationByMousePressed(e)
      return false
    }

    override fun dispose() { }
  }

  private val restoreBoundsRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    if (IDE_FRAME_EVENT_LOG.isDebugEnabled) {
      addComponentListener(EventLogger(frame = this, log = IDE_FRAME_EVENT_LOG))
    }
    IdeEventQueue.getInstance().addDispatcher(mouseActivationWatcher, mouseActivationWatcher)
    launchOnShow("IdeFrameImpl.restoreBoundsRequests") {
      restoreBoundsRequests.collectLatest {
        tryToRestoreValidBounds()
      }
    }
  }

  var frameHelper: FrameHelper? = null
    private set

  var reusedFullScreenState: Boolean = false

  var normalBounds: Rectangle? = null
  var screenBounds: Rectangle? = null
  private var boundsInitialized = false
  private var lastValidBounds: Rectangle? = null

  // when this client property is true, we have to ignore 'resizing' events and not spoil 'normal bounds' value for frame
  @JvmField
  internal var togglingFullScreenInProgress: Boolean = false

  private var lastInactiveMouseXAbs: Int = 0
  private var lastInactiveMouseYAbs: Int = 0
  private var mouseNotPressedYetSinceLastActivation: Boolean = false
  @ApiStatus.Internal
  var wasJustActivatedByClick: Boolean = false
    private set

  private var isDisposed = false

  override fun uiDataSnapshot(sink: DataSink) {
    frameHelper?.uiDataSnapshot(sink)
  }

  interface FrameHelper : UiDataProvider {
    val accessibleName: @Nls String?
    val project: Project?
    val helper: IdeFrame
    val isInFullScreen: Boolean?

    fun dispose()
  }

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
    val maximized = isMaximized(state)

    // do not load FrameInfoHelper class
    if (LoadingState.COMPONENTS_REGISTERED.isOccurred && extendedState == NORMAL && maximized) {
      normalBounds = bounds
      screenBounds = graphicsConfiguration?.bounds
      if (IDE_FRAME_EVENT_LOG.isDebugEnabled) { // avoid unnecessary concatenation
        IDE_FRAME_EVENT_LOG.debug("Saved bounds for IDE frame ${normalBounds} and screen ${screenBounds} before maximizing")
      }
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
    isDisposed = false
    if (hideNativeLinuxTitle(UISettings.shadowInstance) && !isUndecorated) {
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
      Disposer.dispose(mouseActivationWatcher)
      fixSwingLeaks()
      // must be called in addition to the `dispose`, otherwise not removed from `Window.allWindows` list.
      isVisible = false
      super.dispose()
      isDisposed = true
    }
  }

  override fun isWindowDisposed(): Boolean = isDisposed

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

  override fun setVisible(b: Boolean) {
    super.setVisible(b)
    if (b) {
      FUSProjectHotStartUpMeasurer.frameBecameVisible()
    }
  }

  /**
   * Detects whether the frame was activated by a mouse click
   *
   * When the frame is activated, it's impossible to tell the reason,
   * as the JRE doesn't report it, and if the frame was activated by a mouse click,
   * the mouse-pressed event may not even be in the queue yet.
   *
   * Therefore, we detect it using heuristics: we record the mouse coordinates
   * when the frame is inactive and then compare them with the mouse coordinates
   * when the first mouse-pressed event arrives after frame activation.
   * If the coordinates are close enough, the click is likely to be the cause of the activation.
   *
   * This heuristic doesn't work in the case when the user alt-tabs into the frame
   * and then clicks the mouse without moving it much.
   * But it's a highly unlikely sequence of events, and we're willing to accept false positives in such cases.
   */
  private fun detectWindowActivationByMousePressed(e: AWTEvent) {
    if (e.source != this) return
    when (e.id) {
      MouseEvent.MOUSE_MOVED -> {
        e as MouseEvent
        if (!isActive) {
          lastInactiveMouseXAbs = e.xOnScreen
          lastInactiveMouseYAbs = e.yOnScreen
        }
      }
      WindowEvent.WINDOW_ACTIVATED -> {
        mouseNotPressedYetSinceLastActivation = true
      }
      MouseEvent.MOUSE_PRESSED -> {
        e as MouseEvent
        wasJustActivatedByClick =
          mouseNotPressedYetSinceLastActivation &&
          isClose(e.xOnScreen, e.yOnScreen, lastInactiveMouseXAbs, lastInactiveMouseYAbs)
        mouseNotPressedYetSinceLastActivation = false
      }
    }
  }

  @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION") // just for debugging, because all other methods delegate to this one
  override fun reshape(x: Int, y: Int, width: Int, height: Int) {
    super.reshape(x, y, width, height)
    // Only start checking bounds after they first become sensible,
    // because a frame always starts with zero width / height,
    // and that would produce unnecessary error messages in the log.
    if (!boundsInitialized) {
      boundsInitialized = width > 0 || height > 0
    }
    if (boundsInitialized) {
      checkForNonsenseBounds("reshape", width, height)
    }
    IDE_FRAME_EVENT_LOG.traceThrowable {
      Throwable("IdeFrameImpl.reshape(x=$x, y=$y, width=$width, height=$height)")
    }
  }

  /**
   * Fixes the Windows-specific issue with the window suddenly becoming too small.
   */
  internal fun ensureSensibleSize() {
    if (
      !SystemInfoRt.isWindows ||
      !boundsInitialized ||
      // The default value is hardcoded to false here regardless of the default value in registry.properties,
      // because it makes exactly zero sense for this functionality to work before the registry is loaded.
      !Registry.`is`("ide.project.frame.auto.fix.size.windows", false) ||
      !isShowing
    ) {
      return
    }
    val currentBounds = bounds
    if (isValidSize(currentBounds.size)) {
      lastValidBounds = currentBounds
    }
    else {
      check(restoreBoundsRequests.tryEmit(Unit))
    }
  }

  /**
   * Tries to restore the last valid bounds of the frame.
   *
   * Makes several attempts with some delays between them,
   * to account for various exotic cases
   * like the monitor configuration being temporarily unavailable after waking up from sleep/hibernation.
   */
  private suspend fun tryToRestoreValidBounds() {
    val delays = listOf(
      // The first attempt: let all pending move/resize events pass through the queue before restoring.
      10.milliseconds,
      // The second attempt: slow enough for the user to notice the issue, but not to start wondering what's going on.
      100.milliseconds,
      // The last attempt: let's hope that if the issue was caused by a monitor configuration change, the monitor is detected now.
      5.seconds,
    )
    for (delay in delays) {
      delay(delay)
      if (restoreValidBoundsAttempt()) break
    }
  }

  private fun restoreValidBoundsAttempt(): Boolean {
    val currentBounds = bounds
    if (isValidSize(currentBounds.size)) return true // already restored for some reason
    val newBounds = Rectangle(lastValidBounds ?: return false)
    val newBoundsFit = Rectangle(newBounds)
    ScreenUtil.moveRectangleToFitTheScreen(newBoundsFit) // location
    ScreenUtil.fitToScreen(newBoundsFit) // size
    val result = when {
      !isValidSize(newBoundsFit.size) -> {
        IDE_FRAME_EVENT_LOG.warn(
          "The IDE window was externally resized to ${currentBounds.width}x${currentBounds.height}, which is too small." +
          " Restoring the size is impossible because an attempt to fit the last valid bounds ($newBounds) to the screens resulted in $newBoundsFit"
        )
        false
      }
      newBoundsFit == newBounds -> {
        IDE_FRAME_EVENT_LOG.warn(
          "The IDE window was externally resized to ${currentBounds.width}x${currentBounds.height}, which is too small." +
          " Restoring the size to the last valid size: ${newBounds.width}x${newBounds.height}"
        )
        this.bounds = newBounds
        true
      }
      else -> {
        IDE_FRAME_EVENT_LOG.warn(
          "The IDE window was externally resized to ${currentBounds.width}x${currentBounds.height}, which is too small." +
          " Also the last valid bounds are outside the screens now (possibly due to a monitor configuration change): $newBounds." +
          " Moving and resizing to fit the screens, the new bounds will be $newBoundsFit"
        )
        this.bounds = newBoundsFit
        true
      }
    }
    logMonitorConfiguration()
    return result
  }

  private fun logMonitorConfiguration() {
    IDE_FRAME_EVENT_LOG.warn("The current monitor configuration is:")
    for (message in ScreenUtil.loggableMonitorConfiguration(this)) {
      IDE_FRAME_EVENT_LOG.warn(message)
    }
  }
}

private fun isValidSize(size: Dimension): Boolean =
  size.width >= FrameBoundsConverter.MIN_WIDTH && size.height >= FrameBoundsConverter.MIN_HEIGHT

private fun isClose(x1: Int, y1: Int, x2: Int, y2: Int): Boolean {
  val threshold = 3
  return abs(x1 - x2) <= threshold && abs(y1 - y2) <= threshold
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

private fun fixSwingLeaks() {
  fixDragRecognitionSupportLeak()
  fixTooltipManagerLeak()
}

private fun fixDragRecognitionSupportLeak() {
  // sending a "mouse release" event to any DnD-supporting component indirectly calls javax.swing.plaf.basic.DragRecognitionSupport.clearState,
  // cleaning up the potential leak (that can happen if the user started dragging something and released the mouse outside the component)
  val fakeTree = object : Tree() {
    fun releaseDND() {
      processMouseEvent(mouseEvent(this, MouseEvent.MOUSE_RELEASED))
    }
  }
  fakeTree.dragEnabled = true
  fakeTree.releaseDND()
}

private fun fixTooltipManagerLeak() {
  val fakeComponent = JPanel()
  ToolTipManager.sharedInstance().mousePressed(mouseEvent(fakeComponent, MouseEvent.MOUSE_PRESSED))
}

private fun mouseEvent(source: Component, id: Int) = MouseEvent(
  source,
  id,
  System.currentTimeMillis(),
  0,
  0,
  0,
  1,
  false,
  MouseEvent.BUTTON1
)

