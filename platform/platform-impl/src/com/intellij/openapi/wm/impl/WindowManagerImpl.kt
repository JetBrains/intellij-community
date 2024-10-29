// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.wm.impl

import com.intellij.configurationStore.deserializeInto
import com.intellij.configurationStore.serialize
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.idea.AppMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
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
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ScreenUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.sun.jna.platform.WindowUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.JWindow

private val LOG = logger<WindowManagerImpl>()
@JvmField
internal val IDE_FRAME_EVENT_LOG: Logger = Logger.getInstance("ide.frame.events")

@NonNls
private const val FOCUSED_WINDOW_PROPERTY_NAME = "focusedWindow"
@NonNls
private const val FRAME_ELEMENT = "frame"

@State(name = "WindowManager",
       category = SettingsCategory.UI,
       exportable = true,
       storages = [
  Storage(value = "window.state.xml", roamingType = RoamingType.DISABLED, usePathMacroManager = false)
])
class WindowManagerImpl : WindowManagerEx(), PersistentStateComponentWithModificationTracker<Element> {
  private var alphaModeSupported: Boolean? = null
  internal val windowWatcher: WindowWatcher = WindowWatcher()

  internal var oldLayout: DesktopLayout? = null
    private set

  private class ProjectItem(@JvmField val frameHelper: ProjectFrameHelper, private val listener: ComponentListener?) {
    fun release() {
      listener?.let {
        frameHelper.frame.removeComponentListener(it)
      }
    }
  }

  private val projectToFrame = HashMap<Project, ProjectItem>()
  // read from any thread, write from EDT
  private val frameToReuse = AtomicReference<IdeFrameImpl?>()

  internal val defaultFrameInfoHelper: FrameInfoHelper = FrameInfoHelper()

  var frameReuseEnabled = false
    private set
    @Internal get

  init {
    val app = ApplicationManager.getApplication()
    val connection = app.messageBus.simpleConnect()
    if (!app.isUnitTestMode) {
      Disposer.register(app, Disposable { disposeRootFrame() })
      connection.subscribe(TitleInfoProvider.TOPIC, object : TitleInfoProvider.TitleInfoProviderListener {
        override fun configurationChanged() {
          for ((project, item) in projectToFrame) {
            item.frameHelper.updateTitle(project)
          }
        }
      })
    }
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(FOCUSED_WINDOW_PROPERTY_NAME, windowWatcher)

    connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosed(project: Project) {
        val helper = getFrameHelper(project)
        LOG.info("=== Release(${helper != null}) frame on closed project ===")
        helper?.let {
          releaseFrame(it)
        }
      }
    })
  }

  override fun getAllProjectFrames(): Array<ProjectFrameHelper> = projectToFrame.values.map { it.frameHelper }.toTypedArray()

  override fun getProjectFrameHelpers(): List<ProjectFrameHelper> = projectToFrame.values.map { it.frameHelper }

  override fun findVisibleFrame(): JFrame? {
    return projectToFrame.values.firstOrNull()?.frameHelper?.frame ?: WelcomeFrame.getInstance() as? JFrame
  }

  override fun findFirstVisibleFrameHelper(): ProjectFrameHelper? = projectToFrame.values.asSequence().map { it.frameHelper }.firstOrNull()

  override fun getScreenBounds(): Rectangle = ScreenUtil.getAllScreensRectangle()

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

  override fun suggestParentWindow(project: Project?): Window? = windowWatcher.suggestParentWindow(project, this)

  override fun getStatusBar(project: Project): IdeStatusBarImpl? = getFrameHelper(project)?.statusBar

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
  override fun getFrameHelper(project: Project?): ProjectFrameHelper? = projectToFrame.get(project)?.frameHelper

  override fun findFrameHelper(project: Project?): ProjectFrameHelper? {
    return getFrameHelper(project ?: IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project ?: return null)
  }

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

  suspend fun assignFrame(frameHelper: ProjectFrameHelper, project: Project) {
    assignFrame(frameHelper, project, true)
  }

  internal suspend fun assignFrame(frameHelper: ProjectFrameHelper, project: Project, withListener: Boolean) {
    withContext(Dispatchers.EDT) {
      LOG.assertTrue(!projectToFrame.containsKey(project))

      if (withListener) {
        val listener = FrameStateListener(defaultFrameInfoHelper)
        frameHelper.frame.addComponentListener(listener)
        projectToFrame.put(project, ProjectItem(frameHelper, listener))
      }
      else {
        projectToFrame.put(project, ProjectItem(frameHelper, null))
      }
    }
  }

  @RequiresEdt
  override fun releaseFrame(releasedFrameHelper: ProjectFrameHelper) {
    val project = releasedFrameHelper.project
    if (project != null) {
      projectToFrame.remove(project)?.release()

      if (frameReuseEnabled && frameToReuse.get() == null && project !is LightEditCompatible) {
        releasedFrameHelper.storeStateForReuse()
        val frame = releasedFrameHelper.frame
        frameToReuse.set(frame)
        frame.doSetRootPane(null)
        frame.setFrameHelper(null)
        if (JOptionPane.getRootFrame() === frame) {
          JOptionPane.setRootFrame(null)
        }
      }
    }

    runCatching {
      releasedFrameHelper.dispose()
    }.getOrLogException(LOG)
  }

  override fun isFrameReused(helper: ProjectFrameHelper): Boolean = helper.frame === frameToReuse.get()

  internal fun disposeRootFrame() {
    frameToReuse.getAndSet(null)?.doDispose()
  }

  fun withFrameReuseEnabled(): AutoCloseable {
    val oldValue = frameReuseEnabled
    frameReuseEnabled = true
    return AutoCloseable { frameReuseEnabled = oldValue }
  }

  override fun getMostRecentFocusedWindow(): Window? = windowWatcher.focusedWindow

  override fun getFocusedComponent(window: Window): Component? = windowWatcher.getFocusedComponent(window)

  override fun getFocusedComponent(project: Project?): Component? = windowWatcher.getFocusedComponent(project)

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
      layout.readExternal(it)
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

  override fun isFullScreenSupportedInCurrentOS(): Boolean = isFullScreenSupportedInCurrentOs()

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

  // GTW-3304 WindowUtils crashes on X11-enabled RD hosts
  if (AppMode.isRemoteDevHost())
    return false

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

internal class FrameStateListener(private val defaultFrameInfoHelper: FrameInfoHelper) : ComponentAdapter() {
  override fun componentMoved(e: ComponentEvent) {
    update(e)
  }

  override fun componentResized(e: ComponentEvent) {
    update(e)
  }

  private fun update(e: ComponentEvent) {
    val frame = e.component as IdeFrameImpl
    val rootPane = frame.rootPane
    if (rootPane != null && (rootPane.getClientProperty(ScreenUtil.DISPOSE_TEMPORARY) == true || frame.togglingFullScreenInProgress)) {
      return
    }

    val extendedState = frame.extendedState
    val bounds = frame.bounds
    var normalBoundsOnCurrentScreen: Rectangle? = null
    if (rootPane != null) {
      val oldScreen = frame.screenBounds
      val newScreen = frame.graphicsConfiguration?.bounds
      if (extendedState == Frame.NORMAL) {
        frame.normalBounds = bounds
        frame.screenBounds = newScreen
        if (IDE_FRAME_EVENT_LOG.isDebugEnabled) { // avoid unnecessary concatenation
          IDE_FRAME_EVENT_LOG.debug("Updated bounds for IDE frame ${frame.normalBounds} and screen ${frame.screenBounds} after moving/resizing")
        }
      }
      else if (isMaximized(extendedState)) {
        normalBoundsOnCurrentScreen = getNormalFrameBounds(frame, oldScreen, newScreen)
      }
    }

    val frameHelper = frame.frameHelper?.helper as? ProjectFrameHelper ?: return

    val project = frameHelper.project
    if (project == null) {
      // Component moved during project loading - update myDefaultFrameInfo directly.
      // Cannot mark as dirty and compute later, because to convert user space info to device space,
      // we need graphicsConfiguration, but we can get graphicsConfiguration only from frame,
      // but later, when getStateModificationCount or getState is called, there may be no frame at all.
      defaultFrameInfoHelper.updateFrameInfo(frameHelper, frame)
    }
    else if (!project.isDisposed) {
      ProjectFrameBounds.getInstance(project).markDirty(if (isMaximized(extendedState)) normalBoundsOnCurrentScreen else bounds)
    }
  }
}

private fun getNormalFrameBounds(frame: IdeFrameImpl, oldScreen: Rectangle?, newScreen: Rectangle?): Rectangle? {
  val nativeBounds = frame.getNativeNormalBounds()
  if (nativeBounds != null) {
    IDE_FRAME_EVENT_LOG.debug { "Got native bounds: $nativeBounds" }
    FrameBoundsConverter.scaleDown(nativeBounds, frame.graphicsConfiguration)
    IDE_FRAME_EVENT_LOG.debug { "Updated normal frame bounds from native bounds: $nativeBounds" }
    return nativeBounds
  }
  var result: Rectangle? = null
  val normalBounds = frame.normalBounds
  if (normalBounds == null) {
    IDE_FRAME_EVENT_LOG.debug("Not updating frame bounds because normalBounds == null")
  }
  if (normalBounds != null) {
    result = normalBounds
    if (
      oldScreen != null && !oldScreen.isEmpty &&
      newScreen != null && !newScreen.isEmpty &&
      newScreen != oldScreen
    ) {
      // The frame was moved to another screen after it had been maximized, move/scale its "normal" bounds accordingly.
      result = Rectangle(result)
      ScreenUtil.moveAndScale(result, oldScreen, newScreen)
      if (IDE_FRAME_EVENT_LOG.isDebugEnabled) { // avoid unnecessary concatenation
        IDE_FRAME_EVENT_LOG.debug("Updated bounds for IDE frame ${result} after moving from $oldScreen to $newScreen")
      }
    }
    else {
      if (IDE_FRAME_EVENT_LOG.isDebugEnabled) { // avoid unnecessary concatenation
        IDE_FRAME_EVENT_LOG.debug("Frame moved from $oldScreen to $newScreen, not updating normal bounds $normalBounds")
      }
    }
  }
  return result
}
