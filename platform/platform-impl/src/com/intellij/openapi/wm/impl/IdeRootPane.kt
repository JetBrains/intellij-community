// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "LiftReturnOrAssignment")

package com.intellij.openapi.wm.impl

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
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarCentralWidgetProvider
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.impl.FrameInfoHelper.Companion.isFloatingMenuBarSupported
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.MacToolbarFrameHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.MainFrameCustomHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.MenuFrameHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.CustomDecorationPath
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.SelectedEditorFilePath
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.ToolbarFrameHeader
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
import com.intellij.ui.components.JBPanel
import com.intellij.ui.mac.MacWinTabsHandler
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.JBR
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.MouseMotionAdapter
import javax.swing.*

private const val EXTENSION_KEY = "extensionKey"

@Suppress("LeakingThis")
@ApiStatus.Internal
open class IdeRootPane internal constructor(frame: JFrame,
                                            parentDisposable: Disposable,
                                            loadingState: FrameLoadingState?) : JRootPane(), UISettingsListener {
  private var toolbar: JComponent? = null

  internal var statusBar: IdeStatusBarImpl? = null
    private set

  private val northPanel = JBBox.createVerticalBox()
  internal var navBarStatusWidgetComponent: JComponent? = null
    private set

  private var toolWindowPane: ToolWindowPane? = null
  private val glassPaneInitialized: Boolean
  private var fullScreen = false
  internal val isCompactHeader: Boolean get() = ToggleDistractionFreeModeAction.shouldMinimizeCustomHeader() || isLightEdit
  protected open val isLightEdit: Boolean get() = false

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
  private val isToolbarVisible: Boolean
    get() {
      val uiSettings = UISettings.shadowInstance
      val isNewToolbar = ExperimentalUI.isNewUI()
      return ((isNewToolbar && !isToolbarInHeader(uiSettings) && !isCompactHeader)
              || (!isNewToolbar && uiSettings.showMainToolbar))
             && !uiSettings.presentationMode
    }

  init {
    if (SystemInfoRt.isWindows && (StartupUiUtil.isUnderDarcula || UIUtil.isUnderIntelliJLaF())) {
      try {
        windowDecorationStyle = FRAME
      }
      catch (e: Exception) {
        logger<IdeRootPane>().error(e)
      }
    }

    val contentPane = contentPane
    // listen to mouse motion events for a11y
    contentPane.addMouseMotionListener(object : MouseMotionAdapter() {})

    val isDecoratedMenu = isDecoratedMenu
    if (!isDecoratedMenu && !isFloatingMenuBarSupported) {
      jMenuBar = IdeMenuBar.createMenuBar()
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
          MenuFrameHeader(frame = frame, headerTitle = selectedEditorFilePath, ideMenu = IdeMenuBar.createMenuBar())
        }
        helper = DecoratedHelper(
          customFrameTitlePane = customFrameTitlePane,
          selectedEditorFilePath = selectedEditorFilePath,
          isLightEdit = isLightEdit
        )
        layeredPane.add(customFrameTitlePane.getComponent(), (JLayeredPane.DEFAULT_LAYER - 3) as Any)
      }
      else if (hideNativeLinuxTitle) {
        if (!frame.isDisplayable) {
          frame.isUndecorated = true
        }
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
        menuBar = IdeMenuBar.createMenuBar()
        layeredPane.add(menuBar, (JLayeredPane.DEFAULT_LAYER - 1) as Any)
      }
    }

    val glassPane = IdeGlassPaneImpl(rootPane = this, loadingState = loadingState)
    setGlassPane(glassPane)
    glassPaneInitialized = true

    if (hideNativeLinuxTitle) {
      WindowResizeListenerEx(glassPane, frame, JBUI.insets(4), null)
        .install(parentDisposable)
    }

    if (frame is IdeFrameImpl) {
      putClientProperty(UIUtil.NO_BORDER_UNDER_WINDOW_TITLE_KEY, true)
    }

    UIUtil.decorateWindowHeader(this)

    border = UIManager.getBorder("Window.border")

    helper.init(frame, rootPane, parentDisposable)
    updateMainMenuVisibility()

    if (helper.toolbarHolder == null) {
      toolbar = createToolbar()
      northPanel.add(toolbar, 0)
      toolbar!!.isVisible = isToolbarVisible
    }

    if (SystemInfoRt.isMac && JdkEx.isTabbingModeAvailable()) {
      contentPane.add(MacWinTabsHandler.wrapRootPaneNorthSide(this, northPanel), BorderLayout.NORTH)
    }
    else {
      contentPane.add(northPanel, BorderLayout.NORTH)
    }

    @Suppress("LeakingThis")
    contentPane.add(createCenterComponent(frame, parentDisposable), BorderLayout.CENTER)
  }

  companion object {
    /**
     * Returns true if menu should be placed in toolbar instead of menu bar
     */
    internal val isMenuButtonInToolbar: Boolean
      get() = SystemInfoRt.isXWindow && ExperimentalUI.isNewUI() && !UISettings.shadowInstance.separateMainMenu && !hideNativeLinuxTitle

    internal val hideNativeLinuxTitle = SystemInfoRt.isXWindow && ExperimentalUI.isNewUI() && Registry.`is`("ide.linux.hide.native.title")

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

  open fun getToolWindowPane(): ToolWindowPane = toolWindowPane!!

  private fun updateScreenState(isInFullScreen: () -> Boolean) {
    fullScreen = isInFullScreen()
    if (helper is DecoratedHelper) {
      val isCustomFrameHeaderVisible = !fullScreen || SystemInfo.isMac && !isCompactHeader
      helper.customFrameTitlePane.getComponent().isVisible = isCustomFrameHeaderVisible
    }
    else if (SystemInfoRt.isXWindow) {
      val isNewToolbar = ExperimentalUI.isNewUI()

      if (toolbar != null) {
        val uiSettings = UISettings.shadowInstance
         toolbar!!.isVisible = !fullScreen && ((!isCompactHeader && isNewToolbar && !isToolbarInHeader(uiSettings)) || (!isNewToolbar && uiSettings.showMainToolbar))
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
    val contentPane = JBPanel<JBPanel<*>>(BorderLayout())
    contentPane.background = IdeBackgroundUtil.getIdeBackgroundColor()
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
    toolbar = createToolbar()
    northPanel.add(toolbar, 0)
    toolbar!!.isVisible = isToolbarVisible
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
      customFrameTitlePane.updateMenuActions(false)
      customFrameTitlePane.getComponent().repaint()
    }
    else if (menuBar != null) {
      // no decorated menu bar, but there is a regular one, update it directly
      (menuBar as IdeMenuBar).updateMenuActions(false)
      menuBar.repaint()
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
      toolbar = createToolbar()
      northPanel.add(toolbar, 0)
    }

    toolbar!!.isVisible = isToolbarVisible
  }

  private fun updateStatusBarVisibility() {
    val uiSettings = UISettings.shadowInstance
    statusBar!!.isVisible = uiSettings.showStatusBar && !uiSettings.presentationMode
  }

  private fun updateMainMenuVisibility() {
    val uiSettings = UISettings.shadowInstance
    val globalMenuVisible = SystemInfoRt.isLinux && GlobalMenuLinux.isPresented()
    val isNewToolbar = ExperimentalUI.isNewUI()

    // don't show swing-menu when global (system) menu presented
    val visible = SystemInfo.isMacSystemMenu
                  || fullScreen
                  || (!IdeFrameDecorator.isCustomDecorationActive()
                      && !globalMenuVisible
                      && uiSettings.showMainMenu
                      && (!isMenuButtonInToolbar || isCompactHeader && isNewToolbar)
                      && !hideNativeLinuxTitle)

    if (menuBar != null && visible != menuBar.isVisible) {
      menuBar.isVisible = visible
    }
  }

  fun setProject(project: Project) {
    installNorthComponents(project)
    statusBar?.let {
      project.messageBus.simpleConnect().subscribe(StatusBar.Info.TOPIC, it)
    }

    (helper as? DecoratedHelper)?.selectedEditorFilePath?.project = project
  }

  @RequiresEdt
  protected open fun installNorthComponents(project: Project) {
    val northExtensions = IdeRootPaneNorthExtension.EP_NAME.extensionList
    if (northExtensions.isEmpty()) {
      return
    }

    for (extension in northExtensions) {
      val component = extension.createComponent(/* project = */ project, /* isDocked = */ false) ?: continue
      component.putClientProperty(EXTENSION_KEY, extension.key)
      northPanel.add(component)

      if (component is StatusBarCentralWidgetProvider) {
        navBarStatusWidgetComponent = component.createCentralStatusBarComponent()
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
    val frame = frame ?: return
    frame.background = JBColor.PanelBackground
    (frame.balloonLayout as? BalloonLayoutImpl)?.queueRelayout()

    updateScreenState { fullScreen }
  }

  private val frame: IdeFrameImpl?
    get() = ComponentUtil.getParentOfType(IdeFrameImpl::class.java, this)

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

private fun createToolbar(): JComponent {
  if (ExperimentalUI.isNewUI()) {
    val toolbar = MainToolbar()
    toolbar.init(MainToolbar.computeActionGroups(CustomActionsSchema.getInstance()))
    toolbar.border = JBUI.Borders.empty()
    return toolbar
  }
  else {
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