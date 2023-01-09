// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.wm.impl

import com.intellij.configurationStore.deserializeInto
import com.intellij.configurationStore.serialize
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.FrameInfoHelper.Companion.isFullScreenSupportedInCurrentOs
import com.intellij.openapi.wm.impl.FrameInfoHelper.Companion.isMaximized
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.ScreenUtil
import com.sun.jna.platform.WindowUtils
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JWindow

private val LOG = logger<WindowManagerImpl>()

@NonNls
private const val FOCUSED_WINDOW_PROPERTY_NAME = "focusedWindow"
@NonNls
private const val FRAME_ELEMENT = "frame"

@State(name = "WindowManager", storages = [
  Storage(value = "window.state.xml", roamingType = RoamingType.DISABLED, usePathMacroManager = false)
])
class WindowManagerImpl : WindowManagerEx(), PersistentStateComponentWithModificationTracker<Element> {
  private var alphaModeSupported: Boolean? = null
  internal val windowWatcher = WindowWatcher()

  internal var oldLayout: DesktopLayout? = null
    private set

  private val projectToFrame = HashMap<Project, ProjectFrameHelper>()
  // read from any thread, write from EDT
  private val frameToReuse = AtomicReference<IdeFrameImpl?>()

  internal val defaultFrameInfoHelper = FrameInfoHelper()

  private var frameReuseEnabled = false

  init {
    val app = ApplicationManager.getApplication()
    val connection = app.messageBus.simpleConnect()
    if (!app.isUnitTestMode) {
      Disposer.register(app, Disposable { disposeRootFrame() })
      connection.subscribe(TitleInfoProvider.TOPIC, object : TitleInfoProvider.TitleInfoProviderListener {
        override fun configurationChanged() {
          for ((project, frameHelper) in projectToFrame) {
            frameHelper.updateTitle(project)
          }
        }
      })
    }
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(FOCUSED_WINDOW_PROPERTY_NAME, windowWatcher)

    connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosing(project: Project) {
        getFrameHelper(project)?.let {
          releaseFrame(it)
        }
      }
    })
  }

  override fun getAllProjectFrames() = projectToFrame.values.toTypedArray()

  override fun getProjectFrameHelpers() = projectToFrame.values.toList()

  override fun findVisibleFrame(): JFrame? {
    return projectToFrame.values.firstOrNull()?.frame ?: WelcomeFrame.getInstance() as? JFrame
  }

  override fun findFirstVisibleFrameHelper() = projectToFrame.values.firstOrNull()

  override fun getScreenBounds() = ScreenUtil.getAllScreensRectangle()

  override fun getScreenBounds(project: Project): Rectangle? {
    val onScreen = getFrame(project)!!.locationOnScreen
    val devices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
    for (device in devices) {
      val bounds = device.defaultConfiguration.bounds
      if (bounds.contains(onScreen)) {
        return bounds
      }
    }
    return null
  }

  override fun isInsideScreenBounds(x: Int, y: Int, width: Int): Boolean {
    return ScreenUtil.getAllScreensShape().contains(x.toDouble(), y.toDouble(), width.toDouble(), 1.0)
  }

  override fun isAlphaModeSupported(): Boolean {
    var result = alphaModeSupported
    if (result == null) {
      result = calcAlphaModelSupported()
      alphaModeSupported = result
    }
    return result
  }

  override fun setAlphaModeRatio(window: Window, ratio: Float) {
    require(window.isDisplayable && window.isShowing) { "window must be displayable and showing. window=$window" }
    require(ratio in 0.0f..1.0f) { "ratio must be in [0..1] range. ratio=$ratio" }
    if (!isAlphaModeSupported || !isAlphaModeEnabled(window)) {
      return
    }
    setAlphaMode(window, ratio)
  }

  override fun setWindowMask(window: Window, mask: Shape?) {
    try {
      if (GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.isWindowTranslucencySupported(
          GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT)) {
        window.shape = mask
      }
      else {
        WindowUtils.setWindowMask(window, mask)
      }
    }
    catch (e: Throwable) {
      LOG.debug(e)
    }
  }

  override fun setWindowShadow(window: Window, mode: WindowShadowMode) {
    if (window is JWindow) {
      val root = window.rootPane
      root.putClientProperty("Window.shadow", mode != WindowShadowMode.DISABLED)
      root.putClientProperty("Window.style", if (mode == WindowShadowMode.SMALL) "small" else null)
    }
  }

  override fun resetWindow(window: Window) {
    try {
      if (!isAlphaModeSupported) {
        return
      }

      setWindowMask(window, null)
      setAlphaMode(window, 0f)
      setWindowShadow(window, WindowShadowMode.NORMAL)
    }
    catch (e: Throwable) {
      LOG.debug(e)
    }
  }

  override fun isAlphaModeEnabled(window: Window): Boolean {
    require(window.isDisplayable && window.isShowing) { "window must be displayable and showing. window=$window" }
    return isAlphaModeSupported
  }

  override fun setAlphaModeEnabled(window: Window, state: Boolean) {
    require(window.isDisplayable && window.isShowing) { "window must be displayable and showing. window=$window" }
  }

  override fun isNotSuggestAsParent(window: Window): Boolean = windowWatcher.isNotSuggestAsParent(window)

  override fun doNotSuggestAsParent(window: Window) {
    windowWatcher.doNotSuggestAsParent(window)
  }

  override fun dispatchComponentEvent(e: ComponentEvent) {
    windowWatcher.dispatchComponentEvent(e)
  }

  override fun suggestParentWindow(project: Project?) = windowWatcher.suggestParentWindow(project, this)

  override fun getStatusBar(project: Project) = getFrameHelper(project)?.statusBar

  override fun getStatusBar(component: Component, project: Project?): StatusBar? {
    var parent: Component? = component
    while (parent != null) {
      if (parent is IdeFrame) {
        return parent.statusBar
      }
      parent = parent.parent
    }
    return null
  }

  override fun findFrameFor(project: Project?): IdeFrame? {
    return when {
      project == null -> ProjectFrameHelper.getFrameHelper(mostRecentFocusedWindow) ?: tryToFindTheOnlyFrame()
      project.isDefault -> WelcomeFrame.getInstance()
      else -> getFrameHelper(project) ?: getFrameHelper(null)
    }
  }

  override fun getFrame(project: Project?): IdeFrameImpl? {
    // no assert! otherwise, WindowWatcher.suggestParentWindow fails for default project
    //LOG.assertTrue(myProject2Frame.containsKey(project));
    return getFrameHelper(project)?.frame
  }

  @ApiStatus.Internal
  override fun getFrameHelper(project: Project?) = projectToFrame.get(project)

  override fun findFrameHelper(project: Project?): ProjectFrameHelper? {
    return getFrameHelper(project ?: IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project ?: return null)
  }

  @ApiStatus.Internal
  fun getProjectFrameRootPane(project: Project?): IdeRootPane? = projectToFrame.get(project)?.rootPane

  override fun getIdeFrame(project: Project?): IdeFrame? {
    if (project != null) {
      return getFrameHelper(project)
    }

    val window = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
    if (window != null) {
      getIdeFrame(ComponentUtil.findUltimateParent(window))?.let {
        return it
      }
    }

    for (each in Frame.getFrames()) {
      getIdeFrame(each)?.let {
        return it
      }
    }
    return null
  }

  internal fun removeAndGetRootFrame(): IdeFrameImpl? {
    return frameToReuse.getAndSet(null)
  }

  fun assignFrame(frameHelper: ProjectFrameHelper, project: Project) {
    LOG.assertTrue(!projectToFrame.containsKey(project))
    projectToFrame.put(project, frameHelper)
    frameHelper.frame.addComponentListener(FrameStateListener(defaultFrameInfoHelper, frameHelper))
  }

  internal suspend fun lightFrameAssign(project: Project, frameHelper: ProjectFrameHelper) {
    projectToFrame.put(project, frameHelper)
    frameHelper.setProject(project)
    frameHelper.installDefaultProjectStatusBarWidgets(project)
    frameHelper.updateTitle(project)
  }

  override fun releaseFrame(releasedFrameHelper: ProjectFrameHelper) {
    val project = releasedFrameHelper.project
    if (project != null) {
      projectToFrame.remove(project)
      if (frameReuseEnabled && frameToReuse.get() == null && project !is LightEditCompatible) {
        frameToReuse.set(releasedFrameHelper.frame)
        releasedFrameHelper.frame.doSetRootPane(null)
        releasedFrameHelper.frame.setFrameHelper(null)
      }
    }

    try {
      Disposer.dispose(releasedFrameHelper)
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  override fun isFrameReused(helper: ProjectFrameHelper): Boolean = helper.frame === frameToReuse.get()

  fun disposeRootFrame() {
    frameToReuse.getAndSet(null)?.doDispose()
  }

  fun withFrameReuseEnabled(): AutoCloseable {
    val oldValue = frameReuseEnabled
    frameReuseEnabled = true
    return AutoCloseable { frameReuseEnabled = oldValue }
  }

  override fun getMostRecentFocusedWindow() = windowWatcher.focusedWindow

  override fun getFocusedComponent(window: Window) = windowWatcher.getFocusedComponent(window)

  override fun getFocusedComponent(project: Project?) = windowWatcher.getFocusedComponent(project)

  override fun loadState(state: Element) {
    val frameElement = state.getChild(FRAME_ELEMENT)
    if (frameElement != null) {
      val info = FrameInfo()
      frameElement.deserializeInto(info)

      if (info.extendedState and Frame.ICONIFIED > 0) {
        info.extendedState = Frame.NORMAL
      }
      defaultFrameInfoHelper.copyFrom(info)
    }
    state.getChild(DesktopLayout.TAG)?.let {
      val layout = DesktopLayout()
      layout.readExternal(it, ExperimentalUI.isNewUI())
      oldLayout = layout
    }
  }

  override fun getStateModificationCount(): Long {
    return defaultFrameInfoHelper.getModificationCount()
  }

  override fun getState(): Element {
    val state = Element("state")
    defaultFrameInfoHelper.info?.let { serialize(it) }?.let {
      state.addContent(it)
    }
    return state
  }

  override fun isFullScreenSupportedInCurrentOS() = isFullScreenSupportedInCurrentOs()

  override fun updateDefaultFrameInfoOnProjectClose(project: Project) {
    val frameHelper = getFrameHelper(project) ?: return
    defaultFrameInfoHelper.copyFrom(getFrameInfoByFrameHelper(frameHelper))
  }
}

private fun calcAlphaModelSupported(): Boolean {
  val device = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
  if (device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
    return true
  }

  return try {
    WindowUtils.isWindowAlphaSupported()
  }
  catch (e: Throwable) {
    false
  }
}

private fun setAlphaMode(window: Window, ratio: Float) {
  try {
    when {
      SystemInfoRt.isMac -> {
        when (window) {
          is JWindow -> {
            window.rootPane.putClientProperty("Window.alpha", 1.0f - ratio)
          }
          is JDialog -> {
            window.rootPane.putClientProperty("Window.alpha", 1.0f - ratio)
          }
          is JFrame -> {
            window.rootPane.putClientProperty("Window.alpha", 1.0f - ratio)
          }
        }
      }
      GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.isWindowTranslucencySupported(
        GraphicsDevice.WindowTranslucency.TRANSLUCENT) -> {
        window.opacity = 1.0f - ratio
      }
      else -> {
        WindowUtils.setWindowAlpha(window, 1.0f - ratio)
      }
    }
  }
  catch (e: Throwable) {
    LOG.debug(e)
  }
}

private fun tryToFindTheOnlyFrame(): IdeFrame? {
  var candidate: IdeFrameImpl? = null
  for (each in Frame.getFrames()) {
    if (each is IdeFrameImpl) {
      if (candidate == null) {
        candidate = each
      }
      else {
        candidate = null
        break
      }
    }
  }
  return if (candidate == null) null else ProjectFrameHelper.getFrameHelper(candidate)
}

private fun getIdeFrame(component: Component): IdeFrame? {
  return when (component) {
    is IdeFrameImpl -> ProjectFrameHelper.getFrameHelper(component)
    is IdeFrame -> component
    else -> null
  }
}

internal fun getFrameInfoByFrameHelper(frameHelper: ProjectFrameHelper): FrameInfo {
  return updateFrameInfo(frameHelper = frameHelper, frame = frameHelper.frame, lastNormalFrameBounds = null, oldFrameInfo = null)
}

internal class FrameStateListener(
  private val defaultFrameInfoHelper: FrameInfoHelper,
  private val frameHelper: ProjectFrameHelper,
) : ComponentAdapter() {
  override fun componentMoved(e: ComponentEvent) {
    update(e)
  }

  override fun componentResized(e: ComponentEvent) {
    update(e)
  }

  private fun update(e: ComponentEvent) {
    val frame = e.component as IdeFrameImpl
    val rootPane = frame.rootPane
    if (rootPane != null && (rootPane.getClientProperty(ScreenUtil.DISPOSE_TEMPORARY) == true
                             || frame.togglingFullScreenInProgress)) {
      return
    }

    val extendedState = frame.extendedState
    val bounds = frame.bounds
    if (extendedState == Frame.NORMAL && rootPane != null) {
      frame.normalBounds = bounds
    }

    val project = frameHelper.project
    if (project == null) {
      // Component moved during project loading - update myDefaultFrameInfo directly.
      // Cannot mark as dirty and compute later, because to convert user space info to device space,
      // we need graphicsConfiguration, but we can get graphicsConfiguration only from frame,
      // but later, when getStateModificationCount or getState is called, may be no frame at all.
      defaultFrameInfoHelper.updateFrameInfo(frameHelper, frame)
    }
    else if (!project.isDisposed) {
      ProjectFrameBounds.getInstance(project).markDirty(if (isMaximized(extendedState)) null else bounds)
    }
  }
}
