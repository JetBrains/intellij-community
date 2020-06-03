// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.FrameInfoHelper.Companion.isFullScreenSupportedInCurrentOs
import com.intellij.openapi.wm.impl.FrameInfoHelper.Companion.isMaximized
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.openapi.project.impl.createNewProjectFrame
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ScreenUtil
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.UIUtil
import com.sun.jna.platform.WindowUtils
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.*
import java.util.function.Supplier
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JWindow
import javax.swing.SwingUtilities

private val LOG = logger<WindowManagerImpl>()

@NonNls
private const val FOCUSED_WINDOW_PROPERTY_NAME = "focusedWindow"
@NonNls
private const val FRAME_ELEMENT = "frame"

@State(
  name = "WindowManager",
  defaultStateAsResource = true,
  storages = [
    Storage(value = "window.state.xml", roamingType = RoamingType.DISABLED),
    Storage(value = "window.manager.xml", roamingType = RoamingType.DISABLED, deprecated = true)
  ]
)
class WindowManagerImpl : WindowManagerEx(), PersistentStateComponentWithModificationTracker<Element> {
  private var alphaModeSupported: Boolean? = null
  private val eventDispatcher = EventDispatcher.create(WindowManagerListener::class.java)
  internal val windowWatcher = WindowWatcher()

  // default layout
  private var layout = DesktopLayout()

  // null keys must be supported
  // null key - root frame
  private val projectToFrame: MutableMap<Project?, ProjectFrameHelper> = HashMap()

  internal val defaultFrameInfoHelper = FrameInfoHelper()

  private val frameStateListener = object : ComponentAdapter() {
    override fun componentMoved(e: ComponentEvent) {
      update(e)
    }

    override fun componentResized(e: ComponentEvent) {
      update(e)
    }

    private fun update(e: ComponentEvent) {
      val frame = e.component as IdeFrameImpl
      val rootPane = frame.rootPane
      if (rootPane != null && (UIUtil.isClientPropertyTrue(rootPane, ScreenUtil.DISPOSE_TEMPORARY)
          || UIUtil.isClientPropertyTrue(rootPane, IdeFrameImpl.TOGGLING_FULL_SCREEN_IN_PROGRESS))) {
        return
      }
      val extendedState = frame.extendedState
      val bounds = frame.bounds
      if (extendedState == Frame.NORMAL && rootPane != null) {
        rootPane.putClientProperty(IdeFrameImpl.NORMAL_STATE_BOUNDS, bounds)
      }
      val frameHelper = ProjectFrameHelper.getFrameHelper(frame) ?: return
      val project = frameHelper.project
      if (project == null) {
        // Component moved during project loading - update myDefaultFrameInfo directly.
        // Cannot mark as dirty and compute later, because to convert user space info to device space,
        // we need graphicsConfiguration, but we can get graphicsConfiguration only from frame,
        // but later, when getStateModificationCount or getState is called, may be no frame at all.
        defaultFrameInfoHelper.updateFrameInfo(frameHelper)
      }
      else if (!project.isDisposed) {
        ProjectFrameBounds.getInstance(project).markDirty(if (isMaximized(extendedState)) null else bounds)
      }
    }
  }

  init {
    val application = ApplicationManager.getApplication()
    if (!application.isUnitTestMode) {
      Disposer.register(application, Disposable { disposeRootFrame() })
    }
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(FOCUSED_WINDOW_PROPERTY_NAME, windowWatcher)
  }

  override fun getAllProjectFrames() = projectToFrame.values.toTypedArray()

  override fun getProjectFrameHelpers() = projectToFrame.values.toList()

  override fun findVisibleFrame(): JFrame? {
    return projectToFrame.values.firstOrNull()?.frame ?: WelcomeFrame.getInstance() as? JFrame
  }

  override fun findFirstVisibleFrameHelper() = projectToFrame.values.firstOrNull()

  override fun addListener(listener: WindowManagerListener) {
    eventDispatcher.addListener(listener)
  }

  override fun removeListener(listener: WindowManagerListener) {
    eventDispatcher.removeListener(listener)
  }

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
      if (GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT)) {
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

  override fun adjustContainerWindow(component: Component, oldSize: Dimension, newSize: Dimension) {
    val window = SwingUtilities.getWindowAncestor(component) as? JWindow ?: return
    val popup = window.rootPane.getClientProperty(JBPopup.KEY) as JBPopup? ?: return
    if (oldSize.height < newSize.height) {
      val size = popup.size
      size.height += newSize.height - oldSize.height
      popup.size = size
      popup.moveToFitScreen()
    }
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
    val parent = ComponentUtil.findUltimateParent(component)
    if (parent is IdeFrame) {
      return parent.statusBar!!.findChild(component)
    }

    val frame = findFrameFor(project) ?: return null
    return frame.statusBar!!.findChild(component)
  }

  override fun findFrameFor(project: Project?): IdeFrame? {
    return when {
      project == null -> ProjectFrameHelper.getFrameHelper(mostRecentFocusedWindow) ?: tryToFindTheOnlyFrame()
      project.isDefault -> WelcomeFrame.getInstance()
      else -> getFrameHelper(project) ?: getFrameHelper(null)
    }
  }

  override fun getFrame(project: Project?): IdeFrameImpl? {
    // no assert! otherwise WindowWatcher.suggestParentWindow fails for default project
    //LOG.assertTrue(myProject2Frame.containsKey(project));
    return getFrameHelper(project)?.frame
  }

  @ApiStatus.Internal
  override fun getFrameHelper(project: Project?) = projectToFrame.get(project)

  override fun findFrameHelper(project: Project?): ProjectFrameHelper? {
    return getFrameHelper(project ?: IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project ?: return null)
  }

  @ApiStatus.Internal
  fun getProjectFrameRootPane(project: Project?) = projectToFrame.get(project)?.rootPane

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

  internal fun removeAndGetRootFrame() = projectToFrame.remove(null)

  fun assignFrame(frameHelper: ProjectFrameHelper, project: Project) {
    LOG.assertTrue(!projectToFrame.containsKey(project))
    projectToFrame.put(project, frameHelper)
    frameHelper.project = project
    val frame = frameHelper.frame
    frame.title = FrameTitleBuilder.getInstance().getProjectTitle(project)
    frame.addComponentListener(frameStateListener)
  }

  fun allocateFrame(project: Project,
                    projectFrameHelperFactory: Supplier<out ProjectFrameHelper> = Supplier {
                      ProjectFrameHelper(createNewProjectFrame(false), null)
                    }): ProjectFrameHelper {
    var frame = getFrameHelper(project)
    if (frame != null) {
      eventDispatcher.multicaster.frameCreated(frame)
      return frame
    }
    frame = removeAndGetRootFrame()
    val isNewFrame = frame == null
    var frameInfo: FrameInfo? = null
    if (isNewFrame) {
      frame = projectFrameHelperFactory.get()
      frame.init()
      frameInfo = ProjectFrameBounds.getInstance(project).getFrameInfoInDeviceSpace()
      if (frameInfo?.bounds == null) {
        val lastFocusedProject = IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project
        if (lastFocusedProject != null) {
          frameInfo = ProjectFrameBounds.getInstance(lastFocusedProject).getActualFrameInfoInDeviceSpace(frame, this)
        }
        if (frameInfo?.bounds == null) {
          frameInfo = defaultFrameInfoHelper.info
        }
      }
      if (frameInfo?.bounds != null) {
        // update default frame info - newly opened project frame should be the same as last opened
        if (frameInfo !== defaultFrameInfoHelper.info) {
          defaultFrameInfoHelper.copyFrom(frameInfo)
        }
        val bounds = frameInfo.bounds
        if (bounds != null) {
          frame.frame.bounds = FrameBoundsConverter.convertFromDeviceSpaceAndFitToScreen(bounds)
        }
      }
    }
    frame!!.project = project
    projectToFrame.put(project, frame)
    if (isNewFrame) {
      if (frameInfo != null) {
        frame.frame.extendedState = frameInfo.extendedState
      }
      frame.frame.isVisible = true
      if (isFullScreenSupportedInCurrentOs() && frameInfo != null && frameInfo.fullScreen) {
        frame.toggleFullScreen(true)
      }
    }
    if (isNewFrame) {
      frame.frame.addComponentListener(frameStateListener)
      IdeMenuBar.installAppMenuIfNeeded(frame.frame)
    }
    eventDispatcher.multicaster.frameCreated(frame)
    return frame
  }

  override fun releaseFrame(frameHelper: ProjectFrameHelper) {
    eventDispatcher.multicaster.beforeFrameReleased(frameHelper)
    val frame: JFrame = frameHelper.frame
    val project = frameHelper.project!!
    frameHelper.project = null
    frame.title = null
    frameHelper.setFileTitle(null, null)
    projectToFrame.remove(project)
    if (projectToFrame.isEmpty() && project !is LightEditCompatible) {
      projectToFrame.put(null, frameHelper)
    }
    else {
      frameHelper.statusBar?.let {
        Disposer.dispose(it)
      }
      Disposer.dispose(frameHelper)
    }
  }

  fun disposeRootFrame() {
    if (projectToFrame.size == 1) {
      removeAndGetRootFrame()?.let {
        Disposer.dispose(it)
      }
    }
  }

  override fun getMostRecentFocusedWindow() = windowWatcher.focusedWindow

  override fun getFocusedComponent(window: Window) = windowWatcher.getFocusedComponent(window)

  override fun getFocusedComponent(project: Project?) = windowWatcher.getFocusedComponent(project)

  override fun noStateLoaded() {
    var anchor = ToolWindowAnchor.LEFT
    var order = 0
    fun info(id: String, weight: Float = -1f, contentUiType: ToolWindowContentUiType? = null): WindowInfoImpl {
      val result = WindowInfoImpl()
      result.id = id
      result.anchor = anchor
      if (weight != -1f) {
        result.weight = weight
      }
      contentUiType?.let {
        result.contentUiType = it
      }
      result.order = order++
      result.isFromPersistentSettings = false
      result.resetModificationCount()
      return result
    }

    val list = mutableListOf<WindowInfoImpl>()

    // left stripe
    list.add(info(id = "Project", weight = 0.25f, contentUiType = ToolWindowContentUiType.COMBO))

    // bottom stripe
    anchor = ToolWindowAnchor.BOTTOM
    order = 0
    list.add(info(id = "Version Control"))
    list.add(info(id = "Find"))
    list.add(info(id = "Run"))
    list.add(info(id = "Debug", weight = 0.4f))
    list.add(info(id = "Inspection", weight = 0.4f))

    for (info in list) {
      layout.addInfo(info.id!!, info)
    }
  }

  override fun loadState(state: Element) {
    val frameElement = state.getChild(FRAME_ELEMENT)
    if (frameElement != null) {
      val info = FrameInfo()
      frameElement.deserializeInto(info)

      // backward compatibility - old name of extendedState attribute
      if (info.extendedState == Frame.NORMAL) {
        frameElement.getAttributeValue("extended-state")?.let {
          info.extendedState = StringUtil.parseInt(it, Frame.NORMAL)
        }
      }
      if (info.extendedState and Frame.ICONIFIED > 0) {
        info.extendedState = Frame.NORMAL
      }
      defaultFrameInfoHelper.copyFrom(info)
    }
    state.getChild(DesktopLayout.TAG)?.let {
      layout.readExternal(it)
    }
  }

  override fun getStateModificationCount(): Long {
    return defaultFrameInfoHelper.getModificationCount() + layout.stateModificationCount
  }

  override fun getState(): Element {
    val state = Element("state")
    defaultFrameInfoHelper.info?.let { serialize(it) }?.let {
      state.addContent(it)
    }

    // save default layout
    layout.writeExternal(DesktopLayout.TAG)?.let {
      state.addContent(it)
    }
    return state
  }

  override fun getLayout() = layout

  override fun setLayout(layout: DesktopLayout) {
    this.layout = layout.copy()
  }

  override fun isFullScreenSupportedInCurrentOS() = isFullScreenSupportedInCurrentOs()
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
      GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT) -> {
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