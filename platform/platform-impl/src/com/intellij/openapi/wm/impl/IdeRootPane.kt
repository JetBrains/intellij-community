// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "LiftReturnOrAssignment")

package com.intellij.openapi.wm.impl

import com.intellij.accessibility.AccessibilityUtils
import com.intellij.ide.GeneralSettings
import com.intellij.ide.actions.DistractionFreeModeController
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.jdkEx.JdkEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.impl.customFrameDecorations.header.*
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.CustomDecorationPath
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.SelectedEditorFilePath
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.ToolbarFrameHeader
import com.intellij.openapi.wm.impl.headertoolbar.*
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.platform.diagnostic.telemetry.impl.rootTask
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.ide.menu.*
import com.intellij.platform.util.coroutines.childScope
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.toolWindow.ToolWindowPane
import com.intellij.toolWindow.ToolWindowPaneNewButtonManager
import com.intellij.toolWindow.ToolWindowPaneOldButtonManager
import com.intellij.ui.*
import com.intellij.ui.components.JBBox
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.mac.MacWinTabsHandler
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.MouseMotionAdapter
import java.awt.event.WindowStateListener
import javax.accessibility.AccessibleContext
import javax.swing.*

private const val EXTENSION_KEY = "extensionKey"

private inline fun mainToolbarHasNoActions(mainToolbarActionSupplier: () -> List<Pair<ActionGroup, *>>): Boolean {
  return mainToolbarActionSupplier().all { it.first.getChildren(null).isEmpty() }
}


@Suppress("LeakingThis")
@ApiStatus.Internal
open class IdeRootPane internal constructor(private val frame: IdeFrameImpl,
                                            loadingState: FrameLoadingState?,
                                            /**
                                             * @return not-null action group or null to use [IdeActions.GROUP_MAIN_MENU] action group
                                             */
                                            mainMenuActionGroup: ActionGroup? = null) : JRootPane(), UISettingsListener {
  @JvmField
  internal val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("IdeRootPane"))

  private var toolbar: JComponent? = null

  internal var statusBar: IdeStatusBarImpl? = null
    private set

  private val northPanel = JBBox.createVerticalBox()

  private var toolWindowPane: ToolWindowPane? = null
  private val glassPaneInitialized: Boolean
  private var fullScreen = false

  internal inline fun isCompactHeader(mainToolbarActionSupplier: () -> List<Pair<ActionGroup, HorizontalLayout.Group>>): Boolean {
    return when {
      isCompactHeaderFastCheck() -> true
      SystemInfoRt.isMac -> mainToolbarHasNoActions(mainToolbarActionSupplier)
      else -> !UISettings.shadowInstance.separateMainMenu && mainToolbarHasNoActions(mainToolbarActionSupplier)
    }
  }

  internal fun isCompactHeaderFastCheck(): Boolean {
    return DistractionFreeModeController.shouldMinimizeCustomHeader() || isLightEdit || !UISettings.getInstance().showNewMainToolbar
  }

  protected open val isLightEdit: Boolean
    get() = false

  private sealed interface Helper {
    val toolbarHolder: ToolbarHolder?
    val ideMenu: ActionAwareIdeMenuBar?
    val isFloatingMenuBarSupported: Boolean

    fun init(frame: JFrame, pane: JRootPane, coroutineScope: CoroutineScope) {
    }
  }

  private class UndecoratedHelper(override val isFloatingMenuBarSupported: Boolean) : Helper {
    override val toolbarHolder: ToolbarHolder?
      get() = null

    override val ideMenu: ActionAwareIdeMenuBar?
      get() = null

    override fun init(frame: JFrame, pane: JRootPane, coroutineScope: CoroutineScope) {
      ToolbarService.getInstance().setCustomTitleBar(
        window = frame,
        rootPane = pane,
        onDispose = { runnable ->
          coroutineScope.coroutineContext.job.invokeOnCompletion {
            runnable.run()
          }
        },
      )
    }
  }

  private class DecoratedHelper(
    val customFrameTitlePane: MainFrameCustomHeader,
    val selectedEditorFilePath: SelectedEditorFilePath?,
    val isLightEdit: Boolean,
    override val ideMenu: ActionAwareIdeMenuBar,
    override val isFloatingMenuBarSupported: Boolean,
    private val isFullScreen: () -> Boolean
  ) : Helper {
    override val toolbarHolder: ToolbarHolder? get() = (customFrameTitlePane as? ToolbarHolder)
      ?.takeIf { ExperimentalUI.isNewUI() && (isToolbarInHeader(isFullScreen()) || isLightEdit) }
  }

  private val helper: Helper

  private inline fun isToolbarVisible(mainToolbarActionSupplier: () -> List<Pair<ActionGroup, HorizontalLayout.Group>>): Boolean {
    val uiSettings = UISettings.shadowInstance
    val isNewToolbar = ExperimentalUI.isNewUI()
    return ((isNewToolbar && !isToolbarInHeader() && !isCompactHeader(mainToolbarActionSupplier)) ||
            (!isNewToolbar && uiSettings.showMainToolbar && !DistractionFreeModeController.shouldMinimizeCustomHeader()))
  }

  fun isToolbarInHeader() = isToolbarInHeader(fullScreen)

  init {
    if (SystemInfoRt.isWindows) {
      runCatching {
        windowDecorationStyle = FRAME
      }.getOrLogException(logger<IdeRootPane>())
    }

    val contentPane = contentPane
    // listen to mouse motion events for a11y
    contentPane.addMouseMotionListener(object : MouseMotionAdapter() {})

    val isDecoratedMenu = isDecoratedMenu
    val isFloatingMenuBarSupported = isFloatingMenuBarSupported
    if (!isDecoratedMenu && !isFloatingMenuBarSupported) {
      createMacAwareMenuBar(frame = frame, component = this, mainMenuActionGroup = mainMenuActionGroup, coroutineScope.childScope())
      helper = UndecoratedHelper(isFloatingMenuBarSupported = false)
      if (SystemInfoRt.isXWindow && !isMenuButtonInToolbar) {
        installMenuBar(mainMenuActionGroup)
      }
    }
    else {
      if (isDecoratedMenu) {
        val selectedEditorFilePath: SelectedEditorFilePath?
        val ideMenu: ActionAwareIdeMenuBar
        val customFrameTitlePane = if (ExperimentalUI.isNewUI()) {
          selectedEditorFilePath = null
          ideMenu = createMacAwareMenuBar(frame = frame,
                                          component = this,
                                          mainMenuActionGroup = mainMenuActionGroup,
                                          coroutineScope = coroutineScope.childScope())
          if (SystemInfoRt.isMac) {
            MacToolbarFrameHeader(coroutineScope = coroutineScope.childScope(), frame = frame, rootPane = this)
          }
          else {
            ToolbarFrameHeader(coroutineScope = coroutineScope.childScope(),
                               frame = frame,
                               rootPane = this,
                               ideMenuBar = ideMenu as IdeJMenuBar)
          }
        }
        else {
          CustomHeader.enableCustomHeader(frame)

          ideMenu = createMenuBar(coroutineScope.childScope(), frame, mainMenuActionGroup)
          selectedEditorFilePath = CustomDecorationPath(frame)
          MenuFrameHeader(frame = frame,
                          headerTitle = selectedEditorFilePath,
                          ideMenu = ideMenu)
        }
        helper = DecoratedHelper(
          customFrameTitlePane = customFrameTitlePane,
          selectedEditorFilePath = selectedEditorFilePath,
          isLightEdit = isLightEdit,
          ideMenu = ideMenu,
          isFloatingMenuBarSupported = isFloatingMenuBarSupported,
          isFullScreen = { fullScreen }
        )
        layeredPane.add(customFrameTitlePane.getComponent(), (JLayeredPane.DEFAULT_LAYER - 3) as Any)
      }
      else if (hideNativeLinuxTitle) {
        val ideMenu = createMenuBar(coroutineScope = coroutineScope.childScope(), frame = frame, customMenuGroup = mainMenuActionGroup)
        val customFrameTitlePane = ToolbarFrameHeader(coroutineScope = coroutineScope.childScope(),
                                                      frame = frame,
                                                      rootPane = this,
                                                      ideMenuBar = ideMenu)
        helper = DecoratedHelper(
          isFloatingMenuBarSupported = true,
          customFrameTitlePane = customFrameTitlePane,
          selectedEditorFilePath = null,
          isLightEdit = isLightEdit,
          ideMenu = ideMenu,
          isFullScreen = { fullScreen }
        )
        layeredPane.add(customFrameTitlePane.getComponent(), (JLayeredPane.DEFAULT_LAYER - 3) as Any)

        val windowStateListener = WindowStateListener {
          installLinuxBorder()
        }
        frame.addWindowStateListener(windowStateListener)
        coroutineScope.coroutineContext.job.invokeOnCompletion {
          frame.removeWindowStateListener(windowStateListener)
        }
      }
      else {
        helper = UndecoratedHelper(isFloatingMenuBarSupported = true)
      }

      assert(isFloatingMenuBarSupported == helper.isFloatingMenuBarSupported)
      if (helper.isFloatingMenuBarSupported) {
        installMenuBar(mainMenuActionGroup)
      }
    }

    val glassPane = IdeGlassPaneImpl(rootPane = this, loadingState = loadingState, coroutineScope.childScope())
    setGlassPane(glassPane)
    glassPaneInitialized = true

    if (hideNativeLinuxTitle) {
      // Under Wayland, interactive resizing can only be done with the help
      // of the server as soon as it involves the change in the location
      // of the window like resizing from the top/left does.
      // Therefore, resizing is implemented entirely in JBR and does not require
      // any additional work. For other toolkits, we resize programmatically
      // with WindowResizeListenerEx
      val toolkitCannotResizeUndecorated = !StartupUiUtil.isWaylandToolkit()
      if (toolkitCannotResizeUndecorated) {
        val windowResizeListener = WindowResizeListenerEx(glassPane = glassPane,
                                                          content = frame,
                                                          border = JBUI.insets(4),
                                                          corner = null)
        windowResizeListener.install(coroutineScope)
        windowResizeListener.setLeftMouseButtonOnly(true)
      }
    }

    putClientProperty(UIUtil.NO_BORDER_UNDER_WINDOW_TITLE_KEY, true)

    ComponentUtil.decorateWindowHeader(this)

    if (SystemInfoRt.isUnix && !SystemInfoRt.isMac) {
      installLinuxBorder()
    }
    else {
      border = UIManager.getBorder("Window.border")
    }

    helper.init(frame = frame, pane = this, coroutineScope = coroutineScope)
    updateMainMenuVisibility()

    if (helper.toolbarHolder == null) {
      coroutineScope.launch(rootTask() + ModalityState.any().asContextElement()) {
        withContext(Dispatchers.EDT) {
          toolbar = createToolbar(coroutineScope.childScope(), frame)
          northPanel.add(toolbar, 0)
          toolbar!!.isVisible = isToolbarVisible(mainToolbarActionSupplier = { computeMainActionGroups() })
        }

        if (!isLightEdit) {
          // init of toolbar in window header is important to make as fast as possible
          // https://youtrack.jetbrains.com/issue/IDEA-323474
          val toolbarHolder = helper.toolbarHolder
          if (toolbarHolder == null && ExperimentalUI.isNewUI()) {
            span("toolbar init") {
              (toolbar as MainToolbar).init()
            }
          }
        }
      }
    }

    if (isLightEdit && ExperimentalUI.isNewUI()) {
      updateToolbar()
    }

    if (SystemInfoRt.isMac && JdkEx.isTabbingModeAvailable()) {
      contentPane.add(MacWinTabsHandler.wrapRootPaneNorthSide(this, northPanel), BorderLayout.NORTH)
    }
    else {
      contentPane.add(northPanel, BorderLayout.NORTH)
    }

    @Suppress("LeakingThis")
    contentPane.add(createCenterComponent(frame), BorderLayout.CENTER)
  }

  companion object {
    /**
     * Returns true if a menu should be placed in toolbar instead of menu bar
     */
    @JvmStatic
    val isMenuButtonInToolbar: Boolean
      @ApiStatus.Internal
      get() = ExperimentalUI.isNewUI() &&
              (SystemInfoRt.isUnix && !SystemInfoRt.isMac && !UISettings.shadowInstance.separateMainMenu && !hideNativeLinuxTitle
               || SystemInfo.isMac && !Menu.isJbScreenMenuEnabled())

    @JvmStatic
    val hideNativeLinuxTitle: Boolean
      @ApiStatus.Internal
      get() = hideNativeLinuxTitleAvailable && hideNativeLinuxTitleSupported && UISettings.shadowInstance.mergeMainMenuWithWindowTitle

    internal val hideNativeLinuxTitleSupported: Boolean
      get() = SystemInfoRt.isUnix && !SystemInfoRt.isMac
              && ExperimentalUI.isNewUI()
              && JBR.isWindowMoveSupported()
              && ((StartupUiUtil.isXToolkit() && !X11UiUtil.isWSL() && !X11UiUtil.isTileWM())
                  || StartupUiUtil.isWaylandToolkit())

    internal val hideNativeLinuxTitleAvailable: Boolean
      get() = SystemInfoRt.isUnix && !SystemInfoRt.isMac
              && ExperimentalUI.isNewUI()
              && Registry.`is`("ide.linux.hide.native.title", false)

    internal fun customizeRawFrame(frame: IdeFrameImpl) {
      // some rootPane is required
      val rootPane = JRootPane()
      if (isDecoratedMenu && !isFloatingMenuBarSupported) {
        CustomHeader.enableCustomHeader(frame)
      }
      frame.doSetRootPane(rootPane)
      if (SystemInfoRt.isMac) {
        MacWinTabsHandler.fastInit(frame)
      }
    }
  }

  internal fun createDecorator(): IdeFrameDecorator? {
    return IdeFrameDecorator.decorate(frame = frame,
                                      glassPane = rootPane.glassPane as IdeGlassPane,
                                      coroutineScope = coroutineScope.childScope())
  }

  @Suppress("unused")
  internal val isCoroutineScopeCancelled: Boolean
    get() = !coroutineScope.isActive

  @ApiStatus.Obsolete
  internal fun createDisposable(): Disposable {
    val disposable = Disposer.newDisposable()
    coroutineScope.coroutineContext.job.invokeOnCompletion { Disposer.dispose(disposable) }
    return disposable
  }

  override fun updateUI() {
    super.updateUI()

    @Suppress("SENSELESS_COMPARISON") // frame = null when called from init of super
    if (frame != null && windowDecorationStyle == NONE) {
      installLinuxBorder()
    }
  }

  protected open fun createCenterComponent(frame: JFrame): Component {
    val paneId = WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
    val toolWindowButtonManager: ToolWindowButtonManager
    if (ExperimentalUI.isNewUI()) {
      toolWindowButtonManager = ToolWindowPaneNewButtonManager(paneId)
      toolWindowButtonManager.add(contentPane as JComponent)
    }
    else {
      toolWindowButtonManager = ToolWindowPaneOldButtonManager(paneId)
    }
    toolWindowPane = ToolWindowPane(frame = frame,
                                    coroutineScope = coroutineScope,
                                    paneId = paneId,
                                    buttonManager = toolWindowButtonManager)
    return toolWindowPane!!
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (!SystemInfoRt.isMac) {
      return super.getAccessibleContext()
    }

    if (accessibleContext == null) {
      // We need to turn IdeRootPane into an accessible group in order to make notifications announcing working
      accessibleContext = object : AccessibleJRootPane() {
        override fun getAccessibleRole() = AccessibilityUtils.GROUPED_ELEMENTS

        override fun getAccessibleName() = UIBundle.message("root.pane.accessible.group.name")
      }
    }
    return accessibleContext
  }

  open fun getToolWindowPane(): ToolWindowPane = toolWindowPane!!

  private fun installMenuBar(mainMenuActionGroup: ActionGroup?) {
    menuBar = createMenuBar(coroutineScope.childScope(), frame, mainMenuActionGroup)
    menuBar.isOpaque = true
    layeredPane.add(menuBar, (JLayeredPane.DEFAULT_LAYER - 1) as Any)
  }

  private fun installLinuxBorder() {
    if (SystemInfoRt.isUnix && !SystemInfoRt.isMac) {
      val maximized = frame.extendedState and Frame.MAXIMIZED_BOTH == Frame.MAXIMIZED_BOTH
      border = JBUI.CurrentTheme.Window.getBorder(!fullScreen && !maximized && hideNativeLinuxTitle)
    }
  }

  private fun updateScreenState(fullScreen: Boolean) {
    this.fullScreen = fullScreen
    installLinuxBorder()
    if (helper is DecoratedHelper) {
      val wasCustomFrameHeaderVisible = helper.customFrameTitlePane.getComponent().isVisible
      val isCustomFrameHeaderVisible = !fullScreen
                                       || !SystemInfoRt.isMac && isToolbarInHeader()
                                       || (SystemInfoRt.isMac && !isCompactHeader { blockingComputeMainActionGroups(CustomActionsSchema.getInstance()) })
      helper.customFrameTitlePane.getComponent().isVisible = isCustomFrameHeaderVisible

      if (wasCustomFrameHeaderVisible != isCustomFrameHeaderVisible) {
        helper.toolbarHolder?.scheduleUpdateToolbar()
        updateToolbarVisibility()
      }
    }
    else if (SystemInfoRt.isUnix && !SystemInfoRt.isMac) {
      toolbar?.isVisible = isToolbarVisible { blockingComputeMainActionGroups(CustomActionsSchema.getInstance()) }
    }

    updateMainMenuVisibility()
  }

  override fun createRootLayout(): LayoutManager {
    return if (isFloatingMenuBarSupported || isDecoratedMenu) MyRootLayout() else super.createRootLayout()
  }

  final override fun setGlassPane(glass: Component) {
    check(!glassPaneInitialized) { "Setting of glass pane for IdeFrame is prohibited" }
    super.setGlassPane(glass)
  }

  /**
   * Invoked when enclosed frame is being disposed.
   */
  override fun removeNotify() {
    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      coroutineScope.cancel()

      jMenuBar = null
      if (helper is DecoratedHelper) {
        layeredPane.remove(helper.customFrameTitlePane.getComponent())
      }
    }
    super.removeNotify()
  }

  override fun createLayeredPane(): JLayeredPane {
    val result = JBLayeredPane()
    result.name = "$name.layeredPane"
    return result
  }

  override fun createContentPane(): Container {
    val contentPane = JPanel(BorderLayout())
    contentPane.background = JBColor.PanelBackground
    return contentPane
  }

  @RequiresEdt
  internal fun preInit(fullScreen: Boolean) {
    if (helper is DecoratedHelper || helper.isFloatingMenuBarSupported) {
      addPropertyChangeListener(IdeFrameDecorator.FULL_SCREEN) {
        val fullScreenProperty = ClientProperty.isTrue(this, IdeFrameDecorator.FULL_SCREEN)
        updateScreenState(fullScreenProperty)
      }
      updateScreenState(fullScreen)
    }
  }

  internal fun updateToolbar() {
    val delegate = helper.toolbarHolder
    if (delegate != null) {
      delegate.scheduleUpdateToolbar()
      return
    }

    toolbar?.let {
      disposeIfNeeded(it)
      northPanel.remove(it)
    }
    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      toolbar = createToolbar(coroutineScope.childScope(), frame)
      northPanel.add(toolbar, 0)
      toolbar!!.isVisible = isToolbarVisible { computeMainActionGroups() }
      contentPane!!.revalidate()
    }
  }

  fun updateNorthComponents() {
    if (isLightEdit) {
      return
    }

    val componentCount = northPanel.componentCount
    if (componentCount == 0 || (componentCount == 1 && northPanel.getComponent(0) === toolbar)) {
      return
    }

    for (i in 0 until componentCount) {
      val component = northPanel.getComponent(i)
      if (component !== toolbar) {
        component.revalidate()
      }
    }
    contentPane!!.revalidate()
  }

  fun updateMainMenuActions() {
    if (helper is DecoratedHelper) {
      val customFrameTitlePane = helper.customFrameTitlePane
      helper.ideMenu.updateMenuActions(forceRebuild = false)
      // The menu bar is decorated, we update it indirectly.
      coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        customFrameTitlePane.updateMenuActions(forceRebuild = false)
        customFrameTitlePane.getComponent().repaint()
      }
    }
    else if (menuBar != null) {
      // no decorated menu bar, but there is a regular one, update it directly
      coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        (menuBar as ActionAwareIdeMenuBar).updateMenuActions(forceRebuild = false)
        menuBar.repaint()
      }
    }
  }

  internal fun createAndConfigureStatusBar(frameHelper: ProjectFrameHelper) {
    val statusBar = createStatusBar(frameHelper)
    this.statusBar = statusBar
    updateStatusBarVisibility()
    contentPane!!.add(statusBar, BorderLayout.SOUTH)
  }

  protected open fun createStatusBar(frameHelper: ProjectFrameHelper): IdeStatusBarImpl {
    return IdeStatusBarImpl(frameHelper = frameHelper,
                            addToolWindowWidget = !ExperimentalUI.isNewUI() && !GeneralSettings.getInstance().isSupportScreenReaders,
                            coroutineScope = coroutineScope.childScope())
  }

  val statusBarHeight: Int
    get() {
      val statusBar = statusBar
      return if (statusBar != null && statusBar.isVisible) statusBar.height else 0
    }

  private fun updateStatusBarVisibility() {
    val uiSettings = UISettings.shadowInstance
    statusBar!!.isVisible = uiSettings.showStatusBar && !uiSettings.presentationMode
  }

  private fun updateMainMenuVisibility() {
    if (menuBar == null) {
      return
    }

    // don't show swing-menu when a global (system) menu presented
    val visible = SystemInfo.isMacSystemMenu
                  || fullScreen
                  || (!IdeFrameDecorator.isCustomDecorationActive()
                      && !(SystemInfoRt.isLinux && GlobalMenuLinux.isPresented())
                      && UISettings.shadowInstance.showMainMenu
                      && (!isMenuButtonInToolbar || isCompactHeader {
      blockingComputeMainActionGroups(CustomActionsSchema.getInstance())
    } && ExperimentalUI.isNewUI()) && !hideNativeLinuxTitle)
    if (visible != menuBar.isVisible) {
      menuBar.isVisible = visible
    }
  }

  suspend fun setProject(project: Project) {
    installNorthComponents(project)
    statusBar?.let {
      project.messageBus.simpleConnect().subscribe(StatusBar.Info.TOPIC, it)
    }

    (helper as? DecoratedHelper)?.selectedEditorFilePath?.project = project
  }

  fun makeComponentToBeMouseTransparentInTitleBar(component: JComponent) {
    if (hideNativeLinuxTitle) {
      WindowMoveListener(this).apply {
        setLeftMouseButtonOnly(true)
        installTo(component)
      }
      return
    }
    val titlePane = (helper as? DecoratedHelper)?.customFrameTitlePane
    val customTitleBar = (titlePane as? CustomHeader)?.customTitleBar ?: (titlePane as? MacToolbarFrameHeader)?.customTitleBar ?: return

    val listener = HeaderClickTransparentListener(customTitleBar)
    component.addMouseListener(listener)
    component.addMouseMotionListener(listener)
  }

  private suspend fun installNorthComponents(project: Project) {
    if (isLightEdit) {
      return
    }

    val northExtensions = IdeRootPaneNorthExtension.EP_NAME.extensionList
    if (northExtensions.isEmpty()) {
      return
    }

    for (extension in northExtensions) {
      val flow = extension.component(project = project, isDocked = false, statusBar = statusBar!!)
      val key = extension.key
      if (flow != null) {
        coroutineScope.launch(ModalityState.any().asContextElement()) {
          flow.collect(FlowCollector { component ->
            withContext(Dispatchers.EDT) {
              if (component == null) {
                val count = northPanel.componentCount
                for (i in count - 1 downTo 0) {
                  val c = northPanel.getComponent(i)
                  if (c is JComponent && c.getClientProperty(EXTENSION_KEY) == key) {
                    northPanel.remove(i)
                    break
                  }
                }
              }
              else {
                component.putClientProperty(EXTENSION_KEY, key)
                northPanel.add(component)
              }
            }
          })
        }
        continue
      }

      withContext(Dispatchers.EDT) {
        val component = extension.createComponent(/* project = */ project, /* isDocked = */ false) ?: return@withContext
        component.putClientProperty(EXTENSION_KEY, key)
        northPanel.add(component)
      }
    }
  }

  fun findNorthUiComponentByKey(key: String): JComponent? {
    return northPanel.components.firstOrNull { (it as? JComponent)?.getClientProperty(EXTENSION_KEY) == key } as? JComponent
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    ComponentUtil.decorateWindowHeader(this)

    updateToolbarVisibility()
    updateStatusBarVisibility()
    val frame = frame
    frame.background = JBColor.PanelBackground
    (frame.balloonLayout as? BalloonLayoutImpl)?.queueRelayout()

    updateScreenState(fullScreen)
  }

  private fun updateToolbarVisibility() {
    if (ExperimentalUI.isNewUI() && SystemInfoRt.isMac) return

    coroutineScope.launch(ModalityState.any().asContextElement()) {
      val isToolbarVisible = isToolbarVisible { computeMainActionGroups() }
      withContext(Dispatchers.EDT) {
        if (toolbar == null) {
          if (!isToolbarVisible) {
            return@withContext
          }

          toolbar = createToolbar(coroutineScope.childScope(), frame)
          northPanel.add(toolbar, 0)
        }
        toolbar!!.isVisible = isToolbarVisible
      }
    }
  }

  private inner class MyRootLayout : RootLayout() {
    // do not cache it - MyRootLayout is created before IdeRootPane constructor
    private val customFrameTitlePane: MainFrameCustomHeader?
      get() = (helper as? DecoratedHelper)?.customFrameTitlePane

    override fun preferredLayoutSize(parent: Container): Dimension {
      return computeLayoutSize(parent) { it.preferredSize }
    }

    override fun minimumLayoutSize(parent: Container): Dimension {
      return computeLayoutSize(parent) { it.preferredSize }
    }

    private inline fun computeLayoutSize(parent: Container, getter: (menuBar: Container) -> Dimension): Dimension {
      val insets = insets
      val rd = contentPane?.let { getter(it) } ?: parent.size
      val dimension = getDimension()
      val menuBarDimension = getMenuBarDimension(getter)
      return Dimension(rd.width.coerceAtLeast(menuBarDimension.width) + insets.left + insets.right + dimension.width,
                       rd.height + menuBarDimension.height + insets.top + insets.bottom + dimension.height)
    }

    override fun maximumLayoutSize(target: Container): Dimension {
      val insets = insets
      val menuBarDimension = getMenuBarDimension { it.maximumSize }
      val dimension = getDimension()
      val rd = if (contentPane != null) {
        contentPane.maximumSize
      }
      else {
        Dimension(Int.MAX_VALUE, Int.MAX_VALUE - insets.top - insets.bottom - menuBarDimension.height - 1)
      }
      return Dimension(rd.width.coerceAtMost(menuBarDimension.width) + insets.left + insets.right + dimension.width,
                       rd.height + menuBarDimension.height + insets.top + insets.bottom + dimension.height)
    }

    private fun getDimension(): Dimension {
      val customFrameTitleComponent = customFrameTitlePane?.getComponent()
      val dimension = if (customFrameTitleComponent != null && customFrameTitleComponent.isVisible) {
        customFrameTitleComponent.preferredSize
      }
      else {
        JBUI.emptySize()
      }
      return dimension
    }

    private inline fun getMenuBarDimension(getter: (menuBar: JComponent) -> Dimension): Dimension {
      val menuBar = menuBar
      if (menuBar != null && menuBar.isVisible && !fullScreen && !isDecoratedMenu) {
        return getter(menuBar)
      }
      else {
        return JBUI.emptySize()
      }
    }

    override fun layoutContainer(parent: Container) {
      val b = parent.bounds
      val i = insets
      val w = b.width - i.right - i.left
      val h = b.height - i.top - i.bottom
      if (layeredPane != null) {
        layeredPane.setBounds(i.left, i.top, w, h)
      }
      glassPane?.setBounds(i.left, i.top, w, h)
      var contentY = 0
      if (menuBar != null && menuBar.isVisible) {
        val mbd = menuBar.preferredSize
        menuBar.setBounds(0, 0, w, mbd.height)
        if (!fullScreen && !isDecoratedMenu) {
          contentY += mbd.height
        }
      }
      val customFrameTitlePane = customFrameTitlePane
      if (customFrameTitlePane != null && customFrameTitlePane.getComponent().isVisible) {
        val tpd = customFrameTitlePane.getComponent().preferredSize
        if (tpd != null) {
          val tpHeight = tpd.height
          customFrameTitlePane.getComponent().setBounds(0, 0, w, tpHeight)
          contentY += tpHeight
        }
      }
      contentPane?.setBounds(0, contentY, w, h - contentY)
    }
  }
}

private val isDecoratedMenu: Boolean
  get() {
    val osSupported = SystemInfoRt.isWindows || (SystemInfoRt.isMac && ExperimentalUI.isNewUI())
    return osSupported && (isToolbarInHeader(false) || IdeFrameDecorator.isCustomDecorationActive())
  }

private suspend fun createToolbar(coroutineScope: CoroutineScope, frame: JFrame): JComponent {
  if (ExperimentalUI.isNewUI()) {
    val toolbar = withContext(Dispatchers.EDT) {
      val toolbar = MainToolbar(coroutineScope = coroutineScope,
                                frame = frame,
                                isOpaque = true,
                                background = JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(true))
      toolbar.border = JBUI.Borders.emptyLeft(5)
      toolbar
    }
    toolbar.init()
    return toolbar
  }
  else {
    // don't bother a client to know that old ui doesn't use coroutine scope
    coroutineScope.cancel()
    val group = CustomActionsSchema.getInstanceAsync().getCorrectedActionAsync(IdeActions.GROUP_MAIN_TOOLBAR)!!
    val toolBar = ActionManagerEx.getInstanceEx().createActionToolbar(ActionPlaces.MAIN_TOOLBAR, group, true)
    toolBar.targetComponent = null
    toolBar.layoutPolicy = ActionToolbar.WRAP_LAYOUT_POLICY
    PopupHandler.installPopupMenu(toolBar.component, "MainToolbarPopupActions", "MainToolbarPopup")
    return toolBar.component
  }
}

private fun disposeIfNeeded(component: JComponent) {
  @Suppress("DEPRECATION")
  if (component is Disposable && !Disposer.isDisposed(component)) {
    Disposer.dispose(component)
  }
}

private fun createMacAwareMenuBar(frame: JFrame,
                                  component: IdeRootPane,
                                  mainMenuActionGroup: ActionGroup? = null,
                                  coroutineScope: CoroutineScope): ActionAwareIdeMenuBar {
  if (SystemInfoRt.isMac) {
    val ideMenu = if (Menu.isJbScreenMenuEnabled()) {
      createMacMenuBar(coroutineScope = coroutineScope,
                       component = component,
                       frame = frame,
                       mainMenuActionGroupProvider = { mainMenuActionGroup ?: getAndWrapMainMenuActionGroup() })
    }
    else {
      val menuBar = IdeJMenuBar(coroutineScope = coroutineScope, frame = frame, customMenuGroup = mainMenuActionGroup)
      // if -DjbScreenMenuBar.enabled=false
      if (frame.rootPane != null) frame.jMenuBar = menuBar
      if (!ExperimentalUI.isNewUI()) component.jMenuBar = menuBar
      menuBar
    }
    return ideMenu
  }
  else {
    return createMenuBar(coroutineScope, frame, mainMenuActionGroup)
  }
}

internal fun createMenuBar(coroutineScope: CoroutineScope, frame: JFrame, customMenuGroup: ActionGroup?): IdeJMenuBar {
  if (SystemInfoRt.isLinux) {
    return LinuxIdeMenuBar(coroutineScope = coroutineScope, frame = frame, customMenuGroup = customMenuGroup)
  }
  else {
    return IdeJMenuBar(coroutineScope = coroutineScope, frame = frame, customMenuGroup = customMenuGroup)
  }
}

internal fun getPreferredWindowHeaderHeight(isCompactHeader: Boolean): Int {
  return JBUI.scale(
    when {
      isCompactHeader -> HEADER_HEIGHT_DFM
      UISettings.getInstance().compactMode -> HEADER_HEIGHT_COMPACT
      else -> HEADER_HEIGHT_NORMAL
    }
  )
}


internal fun configureCustomTitleBar(isCompactHeader: Boolean, customTitleBar: WindowDecorations.CustomTitleBar, frame: JFrame) {
  customTitleBar.height = getPreferredWindowHeaderHeight(isCompactHeader).toFloat()
  JBR.getWindowDecorations()!!.setCustomTitleBar(frame, customTitleBar)
}
