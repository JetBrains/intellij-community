// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "LiftReturnOrAssignment")

package com.intellij.openapi.wm.impl

import com.intellij.accessibility.AccessibilityUtils
import com.intellij.ide.GeneralSettings
import com.intellij.ide.actions.ToggleDistractionFreeModeAction
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
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.impl.FrameInfoHelper.Companion.isFloatingMenuBarSupported
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.MacToolbarFrameHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.MainFrameCustomHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.MenuFrameHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.CustomDecorationPath
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.SelectedEditorFilePath
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.ToolbarFrameHeader
import com.intellij.openapi.wm.impl.headertoolbar.HeaderClickTransparentListener
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.openapi.wm.impl.headertoolbar.isToolbarInHeader
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.toolWindow.ToolWindowButtonManager
import com.intellij.toolWindow.ToolWindowPane
import com.intellij.toolWindow.ToolWindowPaneNewButtonManager
import com.intellij.toolWindow.ToolWindowPaneOldButtonManager
import com.intellij.ui.*
import com.intellij.ui.components.JBBox
import com.intellij.ui.components.JBLayeredPane
import com.intellij.ui.mac.MacWinTabsHandler
import com.intellij.util.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.JBR
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.MouseMotionAdapter
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.*

private const val EXTENSION_KEY = "extensionKey"

@Suppress("LeakingThis")
@ApiStatus.Internal
open class IdeRootPane internal constructor(private val frame: IdeFrameImpl,
                                            parentDisposable: Disposable,
                                            loadingState: FrameLoadingState?) : JRootPane(), UISettingsListener {
  internal val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private var toolbar: JComponent? = null

  internal var statusBar: IdeStatusBarImpl? = null
    private set

  private val northPanel = JBBox.createVerticalBox()

  private var toolWindowPane: ToolWindowPane? = null
  private val glassPaneInitialized: Boolean
  private var fullScreen = false

  internal fun isCompactHeader(mainToolbarActionSupplier: () -> List<Pair<ActionGroup, String>>): Boolean {
    fun mainToolbarHasNoActions(): Boolean {
      return mainToolbarActionSupplier().all { it.first.getChildren(null).isEmpty() }
    }

    return ToggleDistractionFreeModeAction.shouldMinimizeCustomHeader() ||
           isLightEdit ||
           (SystemInfoRt.isMac && mainToolbarHasNoActions()) ||
           (!SystemInfoRt.isMac && !UISettings.shadowInstance.separateMainMenu && mainToolbarHasNoActions())
  }

  protected open val isLightEdit: Boolean
    get() = false

  private sealed interface Helper {
    val toolbarHolder: ToolbarHolder?

    fun init(frame: JFrame, pane: JRootPane, parentDisposable: Disposable) {
    }
  }

  private object UndecoratedHelper : Helper {
    override val toolbarHolder: ToolbarHolder?
      get() = null

    override fun init(frame: JFrame, pane: JRootPane, parentDisposable: Disposable) {
      ToolbarUtil.setCustomTitleBar(frame, pane) { runnable -> Disposer.register(parentDisposable, runnable::run) }
    }
  }

  private class DecoratedHelper(
    val customFrameTitlePane: MainFrameCustomHeader,
    val selectedEditorFilePath: SelectedEditorFilePath?,
    val isLightEdit: Boolean
  ) : Helper {
    override val toolbarHolder: ToolbarHolder? = (customFrameTitlePane as? ToolbarHolder)
      ?.takeIf { ExperimentalUI.isNewUI() && (isToolbarInHeader() || isLightEdit) }
  }

  private val helper: Helper

  private fun isToolbarVisible(mainToolbarActionSupplier: () -> List<Pair<ActionGroup, String>>): Boolean {
    val uiSettings = UISettings.shadowInstance
    val isNewToolbar = ExperimentalUI.isNewUI()
    return !uiSettings.presentationMode &&
           ((isNewToolbar && !isToolbarInHeader() && !isCompactHeader(mainToolbarActionSupplier)) ||
            (!isNewToolbar && uiSettings.showMainToolbar))
  }

  init {
    if (SystemInfoRt.isWindows && (StartupUiUtil.isUnderDarcula || UIUtil.isUnderIntelliJLaF())) {
      runCatching {
        windowDecorationStyle = FRAME
      }.getOrLogException(logger<IdeRootPane>())
    }

    val contentPane = contentPane
    // listen to mouse motion events for a11y
    contentPane.addMouseMotionListener(object : MouseMotionAdapter() {})

    val isDecoratedMenu = isDecoratedMenu
    if (!isDecoratedMenu && !isFloatingMenuBarSupported) {
      jMenuBar = createMenuBar(coroutineScope.childScope(), frame)
      helper = UndecoratedHelper
    }
    else {
      if (isDecoratedMenu) {
        CustomHeader.enableCustomHeader(frame)

        val selectedEditorFilePath: SelectedEditorFilePath?
        val customFrameTitlePane = if (ExperimentalUI.isNewUI()) {
          selectedEditorFilePath = null
          if (SystemInfoRt.isMac) {
            MacToolbarFrameHeader(frame = frame, root = this)
          }
          else {
            ToolbarFrameHeader(frame = frame, root = this)
          }
        }
        else {
          selectedEditorFilePath = CustomDecorationPath(frame)
          MenuFrameHeader(frame = frame, headerTitle = selectedEditorFilePath,
                          ideMenu = createMenuBar(coroutineScope.childScope(), frame))
        }
        helper = DecoratedHelper(
          customFrameTitlePane = customFrameTitlePane,
          selectedEditorFilePath = selectedEditorFilePath,
          isLightEdit = isLightEdit
        )
        layeredPane.add(customFrameTitlePane.getComponent(), (JLayeredPane.DEFAULT_LAYER - 3) as Any)
      }
      else if (hideNativeLinuxTitle) {
        val customFrameTitlePane = ToolbarFrameHeader(frame = frame, root = this)
        helper = DecoratedHelper(
          customFrameTitlePane = customFrameTitlePane,
          selectedEditorFilePath = null,
          isLightEdit = isLightEdit
        )
        layeredPane.add(customFrameTitlePane.getComponent(), (JLayeredPane.DEFAULT_LAYER - 3) as Any)
      }
      else {
        helper = UndecoratedHelper
      }

      if (isFloatingMenuBarSupported) {
        menuBar = createMenuBar(coroutineScope.childScope(), frame)
        menuBar.isOpaque = true
        layeredPane.add(menuBar, (JLayeredPane.DEFAULT_LAYER - 1) as Any)
      }
    }

    val glassPane = IdeGlassPaneImpl(rootPane = this, loadingState = loadingState)
    setGlassPane(glassPane)
    glassPaneInitialized = true

    if (hideNativeLinuxTitle) {
      WindowResizeListenerEx(glassPane, frame, JBUI.insets(4), null).apply {
        install(parentDisposable)
        setLeftMouseButtonOnly(true)
      }
    }

    putClientProperty(UIUtil.NO_BORDER_UNDER_WINDOW_TITLE_KEY, true)

    ComponentUtil.decorateWindowHeader(this)

    border = UIManager.getBorder("Window.border")

    helper.init(frame, rootPane, parentDisposable)
    updateMainMenuVisibility()

    if (helper.toolbarHolder == null) {
      toolbar = createToolbar(coroutineScope.childScope(), frame)
      northPanel.add(toolbar, 0)
      toolbar!!.isVisible = isToolbarVisible(mainToolbarActionSupplier = { MainToolbar.computeActionGroups(CustomActionsSchema.getInstance()) })
    }

    if (SystemInfoRt.isMac && JdkEx.isTabbingModeAvailable()) {
      contentPane.add(MacWinTabsHandler.wrapRootPaneNorthSide(this, northPanel), BorderLayout.NORTH)
    }
    else {
      contentPane.add(northPanel, BorderLayout.NORTH)
    }

    @Suppress("LeakingThis")
    contentPane.add(createCenterComponent(frame, parentDisposable), BorderLayout.CENTER)

    if (isLightEdit && ExperimentalUI.isNewUI()) updateToolbar()
  }

  companion object {
    /**
     * Returns true if a menu should be placed in toolbar instead of menu bar
     */
    internal val isMenuButtonInToolbar: Boolean
      get() = SystemInfoRt.isXWindow && ExperimentalUI.isNewUI() && !UISettings.shadowInstance.separateMainMenu && !hideNativeLinuxTitle

    internal val hideNativeLinuxTitle: Boolean
      get() = hideNativeLinuxTitleAvailable
              && UISettings.shadowInstance.mergeMainMenuWithWindowTitle
              && jbr5777Workaround() && JBR.isWindowMoveSupported()

    internal val hideNativeLinuxTitleAvailable: Boolean
      get() = SystemInfoRt.isXWindow
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

    // Workaround for JBR-5777, should be removed after JBR-5777 is fixed
    fun jbr5777Workaround(): Boolean {
      return GraphicsEnvironment.getLocalGraphicsEnvironment().javaClass.getName().equals("sun.awt.X11GraphicsEnvironment")
    }
  }

  /**
   * @return not-null action group or null to use [IdeActions.GROUP_MAIN_MENU] action group
   */
  open val mainMenuActionGroup: ActionGroup?
    get() = null

  protected open fun createCenterComponent(frame: JFrame, parentDisposable: Disposable): Component {
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
                                    parentDisposable = parentDisposable,
                                    paneId = paneId,
                                    buttonManager = toolWindowButtonManager)
    return toolWindowPane!!
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (SystemInfoRt.isMac) {
      if (accessibleContext == null) {
        // We need to turn IdeRootPane into an accessible group in order to make notifications announcing working
        accessibleContext = object : AccessibleJRootPane() {
          override fun getAccessibleRole(): AccessibleRole {
            return AccessibilityUtils.GROUPED_ELEMENTS
          }

          override fun getAccessibleName(): String {
            return UIBundle.message("root.pane.accessible.group.name")
          }
        }
      }
      return accessibleContext
    }
    else {
      return super.getAccessibleContext()
    }
  }

  open fun getToolWindowPane(): ToolWindowPane = toolWindowPane!!

  private fun updateScreenState(isInFullScreen: () -> Boolean) {
    fullScreen = isInFullScreen()
    if (helper is DecoratedHelper) {
      val isCustomFrameHeaderVisible = !fullScreen || (SystemInfoRt.isMac && !isCompactHeader {
        MainToolbar.computeActionGroups(CustomActionsSchema.getInstance())
      })
      helper.customFrameTitlePane.getComponent().isVisible = isCustomFrameHeaderVisible
    }
    else if (SystemInfoRt.isXWindow) {
      if (toolbar != null) {
        val isNewToolbar = ExperimentalUI.isNewUI()
         toolbar!!.isVisible = !fullScreen && ((!isCompactHeader { MainToolbar.computeActionGroups(CustomActionsSchema.getInstance()) } && isNewToolbar && !isToolbarInHeader()) ||
                                               (!isNewToolbar && UISettings.getInstance().showMainToolbar))
      }
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
    coroutineScope.cancel()

    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      jMenuBar = null
      if (helper is DecoratedHelper) {
        val customFrameTitlePane = helper.customFrameTitlePane
        layeredPane.remove(customFrameTitlePane.getComponent())
        Disposer.dispose(customFrameTitlePane)
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
  internal fun preInit(isInFullScreen: () -> Boolean) {
    if (isDecoratedMenu || isFloatingMenuBarSupported) {
      addPropertyChangeListener(IdeFrameDecorator.FULL_SCREEN) { updateScreenState(isInFullScreen) }
      updateScreenState(isInFullScreen)
    }
  }

  fun initToolbar(actionGroups: List<Pair<ActionGroup, String>>) {
    val toolbarHolder = helper.toolbarHolder
    if (toolbarHolder != null) {
      toolbarHolder.initToolbar(actionGroups)
    }
    else if (ExperimentalUI.isNewUI()) {
      (toolbar as MainToolbar).init(actionGroups)
    }
  }

  internal fun updateToolbar() {
    val delegate = helper.toolbarHolder
    if (delegate != null) {
      delegate.updateToolbar()
      return
    }

    toolbar?.let {
      disposeIfNeeded(it)
      northPanel.remove(it)
    }
    toolbar = createToolbar(coroutineScope.childScope(), frame)
    northPanel.add(toolbar, 0)
    toolbar!!.isVisible = isToolbarVisible { MainToolbar.computeActionGroups(CustomActionsSchema.getInstance()) }
    contentPane!!.revalidate()
  }

  open fun updateNorthComponents() {
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
      // The menu bar is decorated, we update it indirectly.
      coroutineScope.launch {
        customFrameTitlePane.updateMenuActions(forceRebuild = false)
        withContext(Dispatchers.EDT) {
          customFrameTitlePane.getComponent().repaint()
        }
      }
    }
    else if (menuBar != null) {
      // no decorated menu bar, but there is a regular one, update it directly
      coroutineScope.launch {
        (menuBar as ActionAwareIdeMenuBar).updateMenuActions(forceRebuild = false)
        withContext(Dispatchers.EDT) {
          menuBar.repaint()
        }
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
    return IdeStatusBarImpl(disposable = frameHelper,
                            frameHelper = frameHelper,
                            addToolWindowWidget = !ExperimentalUI.isNewUI() && !GeneralSettings.getInstance().isSupportScreenReaders)
  }

  val statusBarHeight: Int
    get() {
      val statusBar = statusBar
      return if (statusBar != null && statusBar.isVisible) statusBar.height else 0
    }

  private fun updateToolbarVisibility() {
    if (toolbar == null) {
      toolbar = createToolbar(coroutineScope.childScope(), frame)
      northPanel.add(toolbar, 0)
    }

    toolbar!!.isVisible = isToolbarVisible { MainToolbar.computeActionGroups(CustomActionsSchema.getInstance()) }
  }

  private fun updateStatusBarVisibility() {
    val uiSettings = UISettings.shadowInstance
    statusBar!!.isVisible = uiSettings.showStatusBar && !uiSettings.presentationMode
  }

  private fun updateMainMenuVisibility() {
    // don't show swing-menu when a global (system) menu presented
    val visible = SystemInfo.isMacSystemMenu
                  || fullScreen
                  || (!IdeFrameDecorator.isCustomDecorationActive()
                      && !(SystemInfoRt.isLinux && GlobalMenuLinux.isPresented())
                      && UISettings.shadowInstance.showMainMenu
                      && (!isMenuButtonInToolbar || isCompactHeader { MainToolbar.computeActionGroups(CustomActionsSchema.getInstance()) } && ExperimentalUI.isNewUI())
                      && !hideNativeLinuxTitle)

    if (menuBar != null && visible != menuBar.isVisible) {
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
      val windowMoveListener = WindowMoveListener(this)
      windowMoveListener.installTo(component)
      return
    }
    val customTitleBar = ((helper as? DecoratedHelper)?.customFrameTitlePane as? CustomHeader)?.customTitleBar ?: return

    val listener = HeaderClickTransparentListener(customTitleBar)
    component.addMouseListener(listener)
    component.addMouseMotionListener(listener)
  }

  protected open suspend fun installNorthComponents(project: Project) {
    val northExtensions = IdeRootPaneNorthExtension.EP_NAME.extensionList
    if (northExtensions.isEmpty()) {
      return
    }

    for (extension in northExtensions) {
      val flow = extension.component(project = project, isDocked = false, statusBar = statusBar!!)
      val key = extension.key
      if (flow != null) {
        coroutineScope.launch {
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

  internal open fun deinstallNorthComponents(project: Project) {
    val count = northPanel.componentCount
    for (i in count - 1 downTo 0) {
      if (northPanel.getComponent(i) !== toolbar) {
        northPanel.remove(i)
      }
    }
  }

  fun findNorthUiComponentByKey(key: String): JComponent? {
    return northPanel.components.firstOrNull { (it as? JComponent)?.getClientProperty(EXTENSION_KEY) == key } as? JComponent
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    UIUtil.decorateWindowHeader(this)
    updateToolbarVisibility()
    updateStatusBarVisibility()
    val frame = frame
    frame.background = JBColor.PanelBackground
    (frame.balloonLayout as? BalloonLayoutImpl)?.queueRelayout()

    updateScreenState { fullScreen }
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
    return osSupported && (isToolbarInHeader() || IdeFrameDecorator.isCustomDecorationActive())
  }

private fun createToolbar(coroutineScope: CoroutineScope, frame: JFrame): JComponent {
  if (ExperimentalUI.isNewUI()) {
    val toolbar = MainToolbar(coroutineScope, frame)
    toolbar.init(MainToolbar.computeActionGroups(CustomActionsSchema.getInstance()))
    toolbar.border = JBUI.Borders.empty(0, 5)
    return toolbar
  }
  else {
    // don't bother a client to know that old ui doesn't use coroutine scope
    coroutineScope.cancel()
    val group = CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_MAIN_TOOLBAR) as ActionGroup
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