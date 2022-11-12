// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.openapi.wm.impl

import com.intellij.ide.GeneralSettings
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.jdkEx.JdkEx
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.application.*
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.*
import com.intellij.openapi.wm.impl.FrameInfoHelper.Companion.isFloatingMenuBarSupported
import com.intellij.openapi.wm.impl.customFrameDecorations.header.MacToolbarFrameHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.MainFrameCustomHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.MenuFrameHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.CustomDecorationPath
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.CustomDecorationPath.Companion.createMainInstance
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
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.JBR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.MouseMotionAdapter
import java.util.function.Consumer
import javax.swing.*

private const val EXTENSION_KEY = "extensionKey"

@Suppress("LeakingThis")
@ApiStatus.Internal
open class IdeRootPane internal constructor(frame: JFrame,
                                            frameHelper: IdeFrame,
                                            parentDisposable: Disposable) : JRootPane(), UISettingsListener {
  private var toolbar: JComponent? = null

  var statusBar: IdeStatusBarImpl? = null
    private set

  private var statusBarDisposed = false
  private val northPanel = JBBox.createVerticalBox()
  private var northExtensions: List<IdeRootPaneNorthExtension> = emptyList()
  internal var navBarStatusWidgetComponent: JComponent? = null
    private set

  private var toolWindowPane: ToolWindowPane? = null
  private val glassPaneInitialized: Boolean
  private var fullScreen = false
  private val customFrameTitlePane: MainFrameCustomHeader?
  private val selectedEditorFilePath: CustomDecorationPath?

  init {
    if (SystemInfoRt.isWindows && (StartupUiUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF())) {
      try {
        windowDecorationStyle = FRAME
      }
      catch (e: Exception) {
        logger<IdeRootPane>().error(e)
      }
    }

    val contentPane = contentPane!!
    if (SystemInfoRt.isMac && JdkEx.isTabbingModeAvailable()) {
      contentPane.add(MacWinTabsHandler.wrapRootPaneNorthSide(this, northPanel), BorderLayout.NORTH)
    }
    else {
      contentPane.add(northPanel, BorderLayout.NORTH)
    }

    // listen to mouse motion events for a11y
    contentPane.addMouseMotionListener(object : MouseMotionAdapter() {})

    val isDecoratedMenu = isDecoratedMenu
    if (!isDecoratedMenu && !isFloatingMenuBarSupported) {
      jMenuBar = IdeMenuBar.createMenuBar()
      selectedEditorFilePath = null
      customFrameTitlePane = null
    }
    else {
      if (isDecoratedMenu) {
        JBR.getCustomWindowDecoration().setCustomDecorationEnabled(frame, true)
        if (SystemInfoRt.isMac) {
          ToolbarUtil.removeMacSystemTitleBar(this)
        }
        selectedEditorFilePath = createMainInstance(frame)
        customFrameTitlePane = if (ExperimentalUI.isNewUI()) {
          if (SystemInfoRt.isMac) {
            MacToolbarFrameHeader(frame = frame, root = this)
          }
          else {
            ToolbarFrameHeader(frame = frame, ideMenu = IdeMenuBar.createMenuBar())
          }
        }
        else {
          MenuFrameHeader(frame = frame, headerTitle = selectedEditorFilePath, ideMenu = IdeMenuBar.createMenuBar())
        }
        layeredPane.add(customFrameTitlePane.getComponent(), (JLayeredPane.DEFAULT_LAYER - 2) as Any)
      }
      else {
        selectedEditorFilePath = null
        customFrameTitlePane = null
      }

      if (isFloatingMenuBarSupported) {
        menuBar = IdeMenuBar.createMenuBar()
        layeredPane.add(menuBar, (JLayeredPane.DEFAULT_LAYER - 1) as Any)
      }
      addPropertyChangeListener(IdeFrameDecorator.FULL_SCREEN) { updateScreenState(frameHelper) }
      updateScreenState(frameHelper)
    }

    val glassPane = IdeGlassPaneImpl(this, false)
    setGlassPane(glassPane)
    glassPaneInitialized = true
    if (frame is IdeFrameImpl) {
      putClientProperty(UIUtil.NO_BORDER_UNDER_WINDOW_TITLE_KEY, java.lang.Boolean.TRUE)
    }

    UIUtil.decorateWindowHeader(this)
    glassPane.isVisible = false

    border = UIManager.getBorder("Window.border")

    if (!isDecoratedMenu) {
      ToolbarUtil.setCustomTitleBar(frame, this) { runnable -> Disposer.register(parentDisposable, runnable::run) }
    }
    updateMainMenuVisibility()
    @Suppress("LeakingThis")
    contentPane.add(createCenterComponent(frame, parentDisposable), BorderLayout.CENTER)
  }

  companion object {
    private fun disposeIfNeeded(component: JComponent) {
      @Suppress("DEPRECATION")
      if (component is Disposable && !Disposer.isDisposed(component)) {
        Disposer.dispose(component)
      }
    }

    @get:ApiStatus.Internal
    val isMenuButtonInToolbar: Boolean
      /**
       * Returns true if menu should be placed in toolbar instead of menu bar
       */
      get() {
        val uiSettings = UISettings.shadowInstance
        return SystemInfoRt.isXWindow && ExperimentalUI.isNewUI() && !uiSettings.separateMainMenu
      }

    private val isDecoratedMenu: Boolean
      get() {
        val osSupported = SystemInfoRt.isWindows || (SystemInfoRt.isMac && ExperimentalUI.isNewUI())
        return osSupported && (isToolbarInHeader() || IdeFrameDecorator.isCustomDecorationActive())
      }

    @ApiStatus.Internal
    fun executeWithPrepareActionManagerAndCustomActionScheme(disposable: Disposable?, task: Consumer<ActionManager>) {
      val app = ApplicationManager.getApplication()
      @Suppress("DEPRECATION")
      val job = app.coroutineScope.launch {
        val componentManager = app as ComponentManagerEx
        val actionManager = componentManager.getServiceAsync(ActionManager::class.java).await()
        componentManager.getServiceAsync(CustomActionsSchema::class.java).join()
        withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
          task.accept(actionManager)
        }
      }

      if (disposable != null) {
        job.cancelOnDispose(disposable)
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

  private fun updateScreenState(helper: IdeFrame) {
    fullScreen = helper.isInFullScreen
    val bar = jMenuBar
    if (isDecoratedMenu) {
      if (bar != null) {
        bar.isVisible = fullScreen
      }
      if (customFrameTitlePane != null) {
        customFrameTitlePane.getComponent().isVisible = !fullScreen
      }
    }
    else if (SystemInfoRt.isXWindow) {
      if (bar != null) {
        bar.isVisible = fullScreen || !isMenuButtonInToolbar
      }
      updateToolbarVisibility(false)
    }
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
      if (!statusBarDisposed) {
        statusBarDisposed = true
        Disposer.dispose(statusBar!!)
      }
      jMenuBar = null
      val customFrameTitlePane = customFrameTitlePane
      if (customFrameTitlePane != null) {
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
  fun prepareToolbar() {
    if (!ExperimentalUI.isNewUI()) {
      return
    }

    val uiSettings = UISettings.shadowInstance
    val isToolbarInHeader = isToolbarInHeader(settings = uiSettings)
    val delegate = if (isToolbarInHeader) customFrameTitlePane as ToolbarHolder? else null
    if (delegate != null) {
      return
    }

    val toolbar = MainToolbar()
    toolbar.border = JBUI.Borders.empty()
    this.toolbar?.let {
      disposeIfNeeded(it)
      northPanel.remove(it)
    }
    this.toolbar = toolbar
    northPanel.add(this.toolbar, 0)
    val visible = !isToolbarInHeader && !uiSettings.presentationMode
    toolbar.isVisible = visible
    contentPane!!.revalidate()
  }

  // returns continuation task that must be performed in EDT
  fun initOrCreateToolbar(actionGroups: List<Pair<ActionGroup, String>>) {
    toolbarHolderDelegate?.let {
      it.initToolbar(actionGroups)
      return
    }

    if (ExperimentalUI.isNewUI()) {
      // null if frame is reused (open project in an existing frame)
      toolbar?.let {
        (it as MainToolbar).init(actionGroups)
        return
      }
    }

    doUpdateToolbarWithoutDelegate()
  }

  fun updateToolbar() {
    val delegate = toolbarHolderDelegate
    if (delegate == null) {
      doUpdateToolbarWithoutDelegate()
    }
    else {
      delegate.updateToolbar()
    }
  }

  private fun doUpdateToolbarWithoutDelegate() {
    removeToolbar()
    toolbar = createToolbar()
    northPanel.add(toolbar, 0)
    updateToolbarVisibility(true)
    contentPane!!.revalidate()
  }

  fun removeToolbar() {
    val delegate = toolbarHolderDelegate
    if (delegate != null) {
      delegate.removeToolbar()
      return
    }
    if (toolbar != null) {
      disposeIfNeeded(toolbar!!)
      northPanel.remove(toolbar)
      toolbar = null
    }
  }

  private val toolbarHolderDelegate: ToolbarHolder?
    get() = if (isToolbarInHeader() && ExperimentalUI.isNewUI()) customFrameTitlePane as ToolbarHolder? else null

  open fun updateNorthComponents() {
    for (i in 0 until northPanel.componentCount) {
      val component = northPanel.getComponent(i)
      if (component !== toolbar) {
        component.revalidate()
      }
    }
    contentPane!!.revalidate()
  }

  fun updateMainMenuActions() {
    val customFrameTitlePane = customFrameTitlePane
    if (customFrameTitlePane != null) {
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

  private fun createToolbar(): JComponent {
    if (ExperimentalUI.isNewUI()) {
      val toolbar = MainToolbar()
      toolbar.init(MainToolbar.computeActionGroups(CustomActionsSchema.getInstance()))
      toolbar.border = JBUI.Borders.empty()
      return toolbar
    }

    val group = CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_MAIN_TOOLBAR) as ActionGroup
    val toolBar = ActionManagerEx.getInstanceEx().createActionToolbar(ActionPlaces.MAIN_TOOLBAR, group, true)
    toolBar.targetComponent = null
    toolBar.layoutPolicy = ActionToolbar.WRAP_LAYOUT_POLICY
    PopupHandler.installPopupMenu(toolBar.component, "MainToolbarPopupActions", "MainToolbarPopup")
    return toolBar.component
  }

  fun createAndConfigureStatusBar(frame: IdeFrame, parentDisposable: Disposable) {
    val statusBar = createStatusBar(frame)
    this.statusBar = statusBar
    Disposer.register(parentDisposable, statusBar)
    updateStatusBarVisibility()
    contentPane!!.add(statusBar, BorderLayout.SOUTH)
  }

  protected open fun createStatusBar(frame: IdeFrame): IdeStatusBarImpl {
    val addToolWindowsWidget = !ExperimentalUI.isNewUI() && !GeneralSettings.getInstance().isSupportScreenReaders
    return IdeStatusBarImpl(frame, addToolWindowsWidget)
  }

  val statusBarHeight: Int
    get() {
      val statusBar = statusBar
      return if (statusBar != null && statusBar.isVisible) statusBar.height else 0
    }

  private fun updateToolbarVisibility(hideInPresentationMode: Boolean) {
    if (toolbar == null) {
      toolbar = createToolbar()
      northPanel.add(toolbar, 0)
    }
    val uiSettings = UISettings.shadowInstance
    val isNewToolbar = ExperimentalUI.isNewUI()
    val visible = ((isNewToolbar && !isToolbarInHeader(uiSettings) || !isNewToolbar && uiSettings.showMainToolbar)
                   && if (hideInPresentationMode) !uiSettings.presentationMode else !fullScreen)
    toolbar!!.isVisible = visible
  }

  private fun updateStatusBarVisibility() {
    val uiSettings = UISettings.shadowInstance
    statusBar!!.isVisible = uiSettings.showStatusBar && !uiSettings.presentationMode
  }

  private fun updateMainMenuVisibility() {
    val uiSettings = UISettings.shadowInstance
    if (uiSettings.presentationMode || IdeFrameDecorator.isCustomDecorationActive()) {
      return
    }

    val globalMenuVisible = SystemInfoRt.isLinux && GlobalMenuLinux.isPresented()
    // don't show swing-menu when global (system) menu presented
    val visible = SystemInfo.isMacSystemMenu || !globalMenuVisible && uiSettings.showMainMenu && !isMenuButtonInToolbar
    if (menuBar != null && visible != menuBar.isVisible) {
      menuBar.isVisible = visible
    }
  }

  fun setProject(project: Project?) {
    selectedEditorFilePath?.project = project
  }

  @RequiresEdt
  internal open fun installNorthComponents(project: Project) {
    northExtensions = IdeRootPaneNorthExtension.EP_NAME.extensionList
    if (northExtensions.isEmpty()) {
      return
    }

    for (extension in northExtensions) {
      val component = extension.createComponent(project, false) ?: continue
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
    updateToolbarVisibility(true)
    updateStatusBarVisibility()
    updateMainMenuVisibility()
    val frame = ComponentUtil.getParentOfType(IdeFrameImpl::class.java, this) ?: return
    frame.background = JBColor.PanelBackground
    (frame.balloonLayout as? BalloonLayoutImpl)?.queueRelayout()
  }

  private inner class MyRootLayout : RootLayout() {
    override fun preferredLayoutSize(parent: Container): Dimension {
      val i = insets
      val rd = if (contentPane == null) parent.size else contentPane.preferredSize
      val dimension = if (customFrameTitlePane != null && customFrameTitlePane.getComponent().isVisible) {
        customFrameTitlePane.getComponent().preferredSize
      }
      else {
        JBUI.emptySize()
      }
      val menuBarDimension = getMenuBarDimension { it.preferredSize }
      return Dimension(rd.width.coerceAtLeast(menuBarDimension.width) + i.left + i.right + dimension.width,
                       rd.height + menuBarDimension.height + i.top + i.bottom + dimension.height)
    }

    override fun minimumLayoutSize(parent: Container): Dimension {
      val i = insets
      val rd = if (contentPane == null) parent.size else contentPane.minimumSize
      val dimension = if (isDecoratedMenu && customFrameTitlePane != null && customFrameTitlePane.getComponent().isVisible) {
        customFrameTitlePane.getComponent().preferredSize
      }
      else {
        JBUI.emptySize()
      }
      val menuBarDimension = getMenuBarDimension { it.minimumSize }
      return Dimension(rd.width.coerceAtLeast(menuBarDimension.width) + i.left + i.right + dimension.width,
                       rd.height + menuBarDimension.height + i.top + i.bottom + dimension.height)
    }

    override fun maximumLayoutSize(target: Container): Dimension {
      val i = insets
      val menuBarDimension = getMenuBarDimension { it.maximumSize }
      val dimension = if (isDecoratedMenu && customFrameTitlePane != null && customFrameTitlePane.getComponent().isVisible) {
        customFrameTitlePane.getComponent().preferredSize
      }
      else {
        JBUI.emptySize()
      }
      val rd = if (contentPane != null) {
        contentPane.maximumSize
      }
      else {
        Dimension(Int.MAX_VALUE,
                  Int.MAX_VALUE - i.top - i.bottom - menuBarDimension.height - 1)
      }
      return Dimension(rd.width.coerceAtMost(menuBarDimension.width) + i.left + i.right + dimension.width,
                       rd.height + menuBarDimension.height + i.top + i.bottom + dimension.height)
    }

    private inline fun getMenuBarDimension(getter: (menuBar: JMenuBar) -> Dimension): Dimension {
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