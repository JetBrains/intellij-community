// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.accessibility.AccessibilityUtils
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.jdkEx.JdkEx
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.IdeGlassPane
import com.intellij.openapi.wm.IdeRootPaneNorthExtension
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.WINDOW_INFO_DEFAULT_TOOL_WINDOW_PANE_ID
import com.intellij.openapi.wm.impl.customFrameDecorations.header.*
import com.intellij.openapi.wm.impl.headertoolbar.MainToolbar
import com.intellij.openapi.wm.impl.headertoolbar.blockingComputeMainActionGroups
import com.intellij.openapi.wm.impl.headertoolbar.computeMainActionGroups
import com.intellij.platform.diagnostic.telemetry.impl.rootTask
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.ide.menu.ActionAwareIdeMenuBar
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
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.jetbrains.WindowDecorations
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.FlowCollector
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.awt.event.MouseMotionAdapter
import javax.accessibility.AccessibleContext
import javax.swing.*

private val EXTENSION_KEY = Key.create<String>("extensionKey")

@Suppress("LeakingThis")
@ApiStatus.Internal
open class IdeRootPane internal constructor(
  parentCs: CoroutineScope,
  private val frame: IdeFrameImpl,
  loadingState: FrameLoadingState?,
  /** a not-null action group, or `null` to use [IdeActions.GROUP_MAIN_MENU] action group */
  mainMenuActionGroup: ActionGroup? = null,
) : JRootPane(), UISettingsListener {
  protected val coroutineScope = parentCs.childScope("IdeRootPane", Dispatchers.Default)

  private var toolbar: JComponent? = null

  private var statusBar: StatusBar? = null

  private val northPanel = JBBox.createVerticalBox()

  private var toolWindowPane: ToolWindowPane? = null
  private val glassPaneInitialized: Boolean
  private var isFullScreen = false

  protected open val isLightEdit: Boolean
    get() = false

  private val helper: FrameHeaderHelper

  init {
    if (SystemInfoRt.isWindows) {
      runCatching {
        windowDecorationStyle = FRAME
      }.getOrLogException(logger<IdeRootPane>())
    }

    val contentPane = contentPane
    // listen to mouse motion events for a11y
    contentPane.addMouseMotionListener(object : MouseMotionAdapter() {})

    helper = CustomWindowHeaderUtil.installCustomHeader(coroutineScope, frame, this, mainMenuActionGroup, isLightEdit, ::isFullScreen)

    val glassPane = IdeGlassPaneImpl(rootPane = this, loadingState, coroutineScope.childScope())
    setGlassPane(glassPane)
    glassPaneInitialized = true

    if (CustomWindowHeaderUtil.hideNativeLinuxTitle(UISettings.shadowInstance)) {
      // Under Wayland, interactive resizing can only be done with the help
      // of the server as soon as it involves the change in the location
      // of the window like resizing from the top/left does.
      // Therefore, resizing is implemented entirely in JBR and does not require
      // any additional work. For other toolkits, we resize programmatically
      // with WindowResizeListenerEx
      val toolkitCannotResizeUndecorated = !StartupUiUtil.isWaylandToolkit()
      if (toolkitCannotResizeUndecorated) {
        val windowResizeListener = WindowResizeListenerEx(glassPane, content = frame, border = JBUI.insets(4), corner = null)
        windowResizeListener.install(coroutineScope)
        windowResizeListener.setLeftMouseButtonOnly(true)
      }
    }

    putClientProperty(UIUtil.NO_BORDER_UNDER_WINDOW_TITLE_KEY, true)

    ComponentUtil.decorateWindowHeader(this)

    helper.init(frame, pane = this, coroutineScope)
    scheduleUpdateMainMenuVisibility()

    if (helper.toolbarHolder == null) {
      coroutineScope.launch(rootTask() + ModalityState.any().asContextElement()) {
        withContext(Dispatchers.EDT) {
          setupToolbar()
          toolbar!!.isVisible = isToolbarVisible(UISettings.shadowInstance, isFullScreen) { computeMainActionGroups() }
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

  private inline fun isCompactHeader(uiSettings: UISettings, mainToolbarActionSupplier: () -> List<Pair<ActionGroup, HorizontalLayout.Group>>): Boolean =
    isLightEdit || CustomWindowHeaderUtil.isCompactHeader(uiSettings, mainToolbarActionSupplier)

  internal fun createDecorator(): IdeFrameDecorator? {
    return IdeFrameDecorator.decorate(frame, rootPane.glassPane as IdeGlassPane, coroutineScope.childScope())
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
    toolWindowPane = ToolWindowPane(frame, coroutineScope, paneId, toolWindowButtonManager)
    return toolWindowPane!!
  }

  override fun getAccessibleContext(): AccessibleContext {
    if (!SystemInfoRt.isMac) {
      return super.getAccessibleContext()
    }

    if (accessibleContext == null) {
      // we need to turn `IdeRootPane` into an accessible group to make notifications announcing working
      accessibleContext = object : AccessibleJRootPane() {
        override fun getAccessibleRole() = AccessibilityUtils.GROUPED_ELEMENTS
        override fun getAccessibleName() = UIBundle.message("root.pane.accessible.group.name")
      }
    }
    return accessibleContext
  }

  open fun getToolWindowPane(): ToolWindowPane = toolWindowPane!!

  internal fun getCustomTitleBar(): WindowDecorations.CustomTitleBar? {
    val titlePane = (helper as? FrameHeaderHelper.Decorated)?.customFrameTitlePane
    return (titlePane as? CustomHeader)?.customTitleBar ?: (titlePane as? MacToolbarFrameHeader)?.customTitleBar
  }

  private fun updateScreenState(uiSettings: UISettings, isFullScreen: Boolean) {
    this.isFullScreen = isFullScreen
    if (helper is FrameHeaderHelper.Decorated) {
      val wasCustomFrameHeaderVisible = helper.customFrameTitlePane.getComponent().isVisible
      val isCustomFrameHeaderVisible = isCustomFrameHeaderVisible(uiSettings, isFullScreen) {
        blockingComputeMainActionGroups(CustomActionsSchema.getInstance())
      }
      helper.customFrameTitlePane.getComponent().isVisible = isCustomFrameHeaderVisible
      if (wasCustomFrameHeaderVisible != isCustomFrameHeaderVisible) {
        helper.toolbarHolder?.scheduleUpdateToolbar()
        updateToolbarVisibility()
      }
    }
    else if (SystemInfoRt.isUnix && !SystemInfoRt.isMac) {
      toolbar?.isVisible = isToolbarVisible(uiSettings, isFullScreen) {
        blockingComputeMainActionGroups(CustomActionsSchema.getInstance())
      }
    }

    scheduleUpdateMainMenuVisibility()
  }

  override fun createRootLayout(): LayoutManager {
    return if (CustomWindowHeaderUtil.isFloatingMenuBarSupported || CustomWindowHeaderUtil.isDecoratedMenu(UISettings.getInstance())) MyRootLayout() else super.createRootLayout()
  }

  final override fun setGlassPane(glass: Component) {
    check(!glassPaneInitialized) { "Setting of glass pane for IdeFrame is prohibited" }
    super.setGlassPane(glass)
  }

  /**
   * Invoked on disposal of the enclosing frame.
   */
  override fun removeNotify() {
    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      coroutineScope.cancel()
      statusBar = null
      jMenuBar = null
      if (helper is FrameHeaderHelper.Decorated) {
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
    if (helper is FrameHeaderHelper.Decorated || helper.isFloatingMenuBarSupported) {
      addPropertyChangeListener(IdeFrameDecorator.FULL_SCREEN) {
        val fullScreenProperty = ClientProperty.isTrue(this, IdeFrameDecorator.FULL_SCREEN)
        updateScreenState(UISettings.getInstance(), fullScreenProperty)
      }
      updateScreenState(UISettings.getInstance(), fullScreen)
    }
  }

  internal fun updateToolbar() {
    val delegate = helper.toolbarHolder
    if (delegate != null) {
      delegate.scheduleUpdateToolbar()
      return
    }

    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      toolbar?.let { it.isVisible = isToolbarVisible(UISettings.shadowInstance, isFullScreen) { computeMainActionGroups() } }
      contentPane!!.revalidate()
    }
  }

  private fun isCustomFrameHeaderVisible(
    uiSettings: UISettings,
    isFullScreen: Boolean,
    mainToolbarActionSupplier: () -> List<Pair<ActionGroup, HorizontalLayout.Group>>,
  ): Boolean {
    return !isFullScreen ||
           !SystemInfoRt.isMac && CustomWindowHeaderUtil.isToolbarInHeader(uiSettings, isFullScreen) ||
           SystemInfoRt.isMac && !isCompactHeader(uiSettings, mainToolbarActionSupplier)
  }

  private inline fun isToolbarVisible(
    uiSettings: UISettings,
    isFullScreen: Boolean,
    mainToolbarActionSupplier: () -> List<Pair<ActionGroup, HorizontalLayout.Group>>,
  ): Boolean {
    val isNewToolbar = ExperimentalUI.isNewUI()
    return isNewToolbar && !CustomWindowHeaderUtil.isToolbarInHeader(uiSettings, isFullScreen) && !isCompactHeader(uiSettings, mainToolbarActionSupplier)
           || !isNewToolbar && uiSettings.showMainToolbar
  }

  fun updateNorthComponents() {
    if (isLightEdit) {
      return
    }

    for (i in 0 until componentCount) {
      val component = northPanel.getComponent(i)
      if (ClientProperty.isSet(component, EXTENSION_KEY)) {
        component.revalidate()
      }
    }
    contentPane!!.revalidate()
  }

  fun updateMainMenuActions() {
    if (helper is FrameHeaderHelper.Decorated) {
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

  @RequiresEdt
  internal fun installStatusBar(statusBar: StatusBar) {
    check(this.statusBar == null) { "Updating a status bar is ot supported" }
    this.statusBar = statusBar
    val component = statusBar.component
    if (component != null) {
      contentPane!!.add(component, BorderLayout.SOUTH)
    }
  }

  private fun scheduleUpdateMainMenuVisibility() {
    if (menuBar == null) {
      return
    }

    coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      // don't show the Swing menu when a global (system) menu is presented
      val visible = if (SystemInfo.isMacSystemMenu || isFullScreen) {
        true
      }
      else {
        val uiSettings = UISettings.shadowInstance
        !IdeFrameDecorator.isCustomDecorationActive() && uiSettings.showMainMenu && !CustomWindowHeaderUtil.hideNativeLinuxTitle(uiSettings) &&
        (!CustomWindowHeaderUtil.isMenuButtonInToolbar(uiSettings) || (ExperimentalUI.isNewUI() && isCompactHeader(uiSettings) { computeMainActionGroups() }))
      }
      if (visible != menuBar.isVisible) {
        menuBar.isVisible = visible
      }
    }
  }

  suspend fun setProject(project: Project) {
    installNorthComponents(project)

    (helper as? FrameHeaderHelper.Decorated)?.selectedEditorFilePath?.project = project
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
                  if (ClientProperty.isSet(c, EXTENSION_KEY, key)) {
                    northPanel.remove(i)
                    break
                  }
                }
              }
              else {
                ClientProperty.put(component, EXTENSION_KEY, key)
                northPanel.add(component)
              }
            }
          })
        }
        continue
      }

      withContext(Dispatchers.EDT) {
        extension.createComponent(project, isDocked = false)?.let {
          ClientProperty.put(it, EXTENSION_KEY, key)
          northPanel.add(it)
        }
      }
    }
  }

  fun findNorthUiComponentByKey(key: String): JComponent? =
    northPanel.components.firstOrNull { ClientProperty.isSet(it, EXTENSION_KEY, key) } as? JComponent

  override fun uiSettingsChanged(uiSettings: UISettings) {
    ComponentUtil.decorateWindowHeader(this)
    updateToolbarVisibility()
    updateScreenState(uiSettings, isFullScreen)
  }

  private fun updateToolbarVisibility() {
    if (ExperimentalUI.isNewUI() && SystemInfoRt.isMac) {
      return
    }

    coroutineScope.launch(ModalityState.any().asContextElement()) {
      val isToolbarVisible = isToolbarVisible(UISettings.shadowInstance, isFullScreen) { computeMainActionGroups() }
      withContext(Dispatchers.EDT) {
        if (toolbar == null) {
          if (!isToolbarVisible) {
            return@withContext
          }
          setupToolbar()
        }
        toolbar!!.isVisible = isToolbarVisible
      }
    }
  }

  private suspend fun IdeRootPane.setupToolbar() {
    val newToolbar = createToolbar(coroutineScope.childScope(), frame)
    // createToolbar method can suspend current computation and toolbar can be initialized in another coroutine
    // So we have to check if toolbar is null AFTER the createToolbar call. (see IJPL-43557)
    if (toolbar == null) {
      toolbar = newToolbar
      northPanel.add(toolbar, 0)
    }
  }

  private inner class MyRootLayout : RootLayout() {
    // do not cache it - MyRootLayout is created before IdeRootPane constructor
    private val customFrameTitlePane: MainFrameCustomHeader?
      get() = (helper as? FrameHeaderHelper.Decorated)?.customFrameTitlePane

    override fun preferredLayoutSize(parent: Container): Dimension = computeLayoutSize(parent) { it.preferredSize }

    override fun minimumLayoutSize(parent: Container): Dimension = computeLayoutSize(parent) { it.preferredSize }

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
      return if (menuBar != null && menuBar.isVisible && !isFullScreen && !CustomWindowHeaderUtil.isDecoratedMenu(UISettings.getInstance())) getter(menuBar) else JBUI.emptySize()
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
        if (!isFullScreen && !CustomWindowHeaderUtil.isDecoratedMenu(UISettings.getInstance())) {
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

  private suspend fun createToolbar(coroutineScope: CoroutineScope, frame: JFrame): JComponent {
    if (ExperimentalUI.isNewUI()) {
      val toolbar = withContext(Dispatchers.EDT) {
        val toolbar = MainToolbar(
          coroutineScope = coroutineScope,
          frame = frame,
          isOpaque = true,
          background = JBUI.CurrentTheme.CustomFrameDecorations.mainToolbarBackground(true),
          ::isFullScreen
        )
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
      toolBar.layoutStrategy = ToolbarLayoutStrategy.WRAP_STRATEGY
      PopupHandler.installPopupMenu(toolBar.component, "MainToolbarPopupActions", "MainToolbarPopup")
      return toolBar.component
    }
  }
}
