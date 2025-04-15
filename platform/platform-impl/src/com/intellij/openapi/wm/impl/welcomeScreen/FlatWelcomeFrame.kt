// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.icons.AllIcons
import com.intellij.ide.*
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.lightEdit.LightEditServiceListener
import com.intellij.ide.ui.LafManagerListener
import com.intellij.idea.AppMode
import com.intellij.notification.NotificationsManager
import com.intellij.notification.impl.NotificationsManagerImpl
import com.intellij.openapi.MnemonicHelper
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.WindowStateService
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.openapi.wm.impl.IdeGlassPaneImpl
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomFrameDialogContent.Companion.getCustomContentHolder
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomHeader
import com.intellij.openapi.wm.impl.customFrameDecorations.header.DefaultFrameHeader
import com.intellij.openapi.wm.impl.executeOnCancelInEdt
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenComponentFactory.JActionLinkPanel
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.menu.IdeJMenuBar
import com.intellij.platform.ide.menu.createMacMenuBar
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.labels.ActionLink
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.ui.mac.touchbar.Touchbar
import com.intellij.ui.mac.touchbar.TouchbarActionCustomizations
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.IconUtil
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextAccessor
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import net.miginfocom.swing.MigLayout
import java.awt.*
import java.awt.dnd.*
import java.awt.event.*
import javax.accessibility.AccessibleContext
import javax.swing.*
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

/**
 * @author Konstantin Bulenkov
 */
@Suppress("LeakingThis")
open class FlatWelcomeFrame @JvmOverloads constructor(
  suggestedScreen: AbstractWelcomeScreen? = if (USE_TABBED_WELCOME_SCREEN) TabbedWelcomeScreen() else null
) : JFrame(), IdeFrame, AccessibleContextAccessor, DisposableWindow {
  val screen: AbstractWelcomeScreen
  private val content: Wrapper
  private var balloonLayout: WelcomeBalloonLayoutImpl?
  private var isDisposed = false
  private var header: DefaultFrameHeader? = null

  private val coroutineScope = service<CoreUiCoroutineScopeHolder>().coroutineScope.childScope()
  private val displayChangeListener = DisplayChangeDetector.Listener { updateComponentsAndResize() }

  companion object {
    @JvmField
    var USE_TABBED_WELCOME_SCREEN: Boolean = java.lang.Boolean.parseBoolean(System.getProperty("use.tabbed.welcome.screen", "true"))
    const val BOTTOM_PANEL: String = "BOTTOM_PANEL"
    const val CUSTOM_HEADER: String = "CUSTOM_HEADER"

    @JvmField
    val DEFAULT_HEIGHT: Int = if (USE_TABBED_WELCOME_SCREEN) System.getProperty("welcome.screen.defaultHeight", "650").toInt() else 460

    @JvmField
    val MAX_DEFAULT_WIDTH: Int = System.getProperty("welcome.screen.defaultWidth", "800").toInt()

    private fun saveSizeAndLocation(location: Rectangle) {
      val middle = Point(location.x + location.width / 2, location.y + location.height / 2)
      val windowStateService = WindowStateService.getInstance()
      windowStateService.putLocation(WelcomeFrame.DIMENSION_KEY, middle)
      windowStateService.putSize(WelcomeFrame.DIMENSION_KEY, location.size)
    }

    @JvmStatic
    fun getPreferredFocusedComponent(pair: Pair<JPanel?, JBList<AnAction?>>): JComponent {
      if (pair.second.model.size == 1) {
        val textField = UIUtil.uiTraverser(pair.first).filter(JBTextField::class.java).first()
        if (textField != null) {
          return textField
        }
      }
      return pair.second
    }
  }

  init {
    val rootPane = getRootPane()
    balloonLayout = createBalloonLayout()

    screen = suggestedScreen ?: FlatWelcomeScreen(frame = this)
    executeOnCancelInEdt(coroutineScope) {
      Disposer.dispose(screen)
    }

    content = Wrapper()
    contentPane = content
    if (IdeFrameDecorator.isCustomDecorationActive()) {
      header = DefaultFrameHeader(this, isForDockContainerProvider = false)
      content.setContent(getCustomContentHolder(this, screen.welcomePanel, header!!))
      layeredPane.putClientProperty(CUSTOM_HEADER, header)
    }
    else {
      createWelcomeMenuBar(this, coroutineScope)
      content.setContent(screen.welcomePanel)
    }
    val glassPane = IdeGlassPaneImpl(rootPane)
    setGlassPane(glassPane)
    glassPane.isVisible = false
    updateComponentsAndResize()

    // at this point, window insets may be unavailable, so we need to resize the window when it is shown
    UiNotifyConnector.doWhenFirstShown(this, ::pack)
    val app = ApplicationManager.getApplication()
    val connection = app.messageBus.connect(coroutineScope)
    connection.subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      @Suppress("OVERRIDE_DEPRECATION")
      override fun projectOpened(project: Project) {
        dispose()
      }
    })
    connection.subscribe(LightEditServiceListener.TOPIC, object : LightEditServiceListener {
      override fun lightEditWindowOpened(project: Project) {
        dispose()
      }
    })
    connection.subscribe(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
      override fun appClosing() {
        saveSizeAndLocation(bounds)
      }
    })
    connection.subscribe(LafManagerListener.TOPIC, LafManagerListener {
      balloonLayout?.dispose()
      balloonLayout = createBalloonLayout()
      updateComponentsAndResize()
      repaint()
    })
    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(componentEvent: ComponentEvent) {
        if (WindowStateService.getInstance().getSize(WelcomeFrame.DIMENSION_KEY) != null) {
          saveSizeAndLocation(bounds)
        }
      }
    })
    if (AppMode.isRemoteDevHost()) {
      // The welcome frame is created and positioned before a client connects,
      // so it needs to be repositioned when information about client display arrives.
      DisplayChangeDetector.getInstance().addListener(displayChangeListener)
    }

    setupCloseAction()
    MnemonicHelper.init(this)
    ComponentUtil.decorateWindowHeader(getRootPane())
    ToolbarService.getInstance().setTransparentTitleBar(
      window = this,
      rootPane = getRootPane(),
      onDispose = { runnable ->
        coroutineScope.coroutineContext.job.invokeOnCompletion {
          runnable.run()
        }
      },
    )
    app.invokeLater({ (NotificationsManager.getNotificationsManager() as NotificationsManagerImpl).dispatchEarlyNotifications() },
                    ModalityState.nonModal())
  }

  private fun createBalloonLayout(): WelcomeBalloonLayoutImpl {
    val insets = JBUI.insets(8)
    if (ExperimentalUI.isNewUI()) {
      return WelcomeSeparateBalloonLayoutImpl(rootPane, insets)
    }
    return WelcomeBalloonLayoutImpl(rootPane, insets)
  }

  override fun removeNotify() {
    super.removeNotify()

    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      coroutineScope.cancel()
    }
  }

  protected open fun setupCloseAction() {
    WelcomeFrame.setupCloseAction(this)
  }

  private fun updateComponentsAndResize() {
    val defaultHeight = DEFAULT_HEIGHT
    if (IdeFrameDecorator.isCustomDecorationActive()) {
      val backgroundColor = UIManager.getColor("WelcomeScreen.background")
      if (backgroundColor != null) {
        header!!.background = backgroundColor
      }
    }
    else {
      createWelcomeMenuBar(this, coroutineScope)
      content.setContent(screen.welcomePanel)
    }
    if (USE_TABBED_WELCOME_SCREEN) {
      val defaultSize = JBUI.size(MAX_DEFAULT_WIDTH, defaultHeight)
      preferredSize = WindowStateService.getInstance().getSize(WelcomeFrame.DIMENSION_KEY) ?: defaultSize
      minimumSize = defaultSize
    }
    else {
      val width = if (RecentProjectListActionProvider.getInstance().getActions(addClearListItem = false).isEmpty()) {
        666
      }
      else {
        MAX_DEFAULT_WIDTH
      }
      preferredSize = JBUI.size(width, defaultHeight)
    }
    isResizable = USE_TABBED_WELCOME_SCREEN
    val size = preferredSize
    val location = WindowStateService.getInstance().getLocation(WelcomeFrame.DIMENSION_KEY)
    val screenBounds = ScreenUtil.getScreenRectangle(location ?: Point(0, 0))
    setBounds(
      screenBounds.x + (screenBounds.width - size.width) / 2,
      screenBounds.y + (screenBounds.height - size.height) / 3,
      size.width,
      size.height
    )
    ComponentUtil.decorateWindowHeader(getRootPane())
    title = ""
    title = welcomeFrameTitle
    updateAppWindowIcon(this)
  }

  override fun addNotify() {
    if (IdeFrameDecorator.isCustomDecorationActive()) {
      CustomHeader.enableCustomHeader(this)
    }
    super.addNotify()
  }

  override fun dispose() {
    coroutineScope.cancel()
    if (isDisposed) {
      return
    }

    isDisposed = true
    super.dispose()
    balloonLayout?.let {
      balloonLayout = null
      it.dispose()
    }
    Disposer.dispose(screen)
    WelcomeFrame.resetInstance()
    if (AppMode.isRemoteDevHost()) {
      DisplayChangeDetector.getInstance().removeListener(displayChangeListener)
    }
  }

  override fun isWindowDisposed(): Boolean = isDisposed

  override fun getStatusBar(): StatusBar? = null

  override fun getCurrentAccessibleContext(): AccessibleContext? = accessibleContext

  private val welcomeFrameTitle: String
    get() = WelcomeScreenComponentFactory.getApplicationTitle()

  @Suppress("unused")
  protected fun extendActionsGroup(panel: JPanel?) {
  }

  @Suppress("unused")
  protected fun onFirstActionShown(action: Component) {
  }

  override fun getBalloonLayout(): BalloonLayout? = balloonLayout

  override fun suggestChildFrameBounds(): Rectangle = bounds

  override fun getProject(): Project? {
    return if (ApplicationManager.getApplication().isDisposed) null else ProjectManager.getInstance().defaultProject
  }

  override fun setFrameTitle(title: String) {
    setTitle(title)
  }

  override fun getComponent(): JComponent = getRootPane()

  private class FlatWelcomeScreen(private val frame: FlatWelcomeFrame) : AbstractWelcomeScreen() {
    private val touchbarActions = DefaultActionGroup()
    private var inDnd = false

    init {
      background = WelcomeScreenUIManager.getMainBackground()
      if (RecentProjectListActionProvider.getInstance().getActions(addClearListItem = false, useGroups = true).isNotEmpty()) {
        val recentProjects = WelcomeScreenComponentFactory.createRecentProjects(this)
        add(recentProjects, BorderLayout.WEST)
        val projectsList = UIUtil.findComponentOfType(recentProjects, JList::class.java)
        if (projectsList != null) {
          projectsList.model.addListDataListener(object : ListDataListener {
            override fun intervalAdded(e: ListDataEvent) {}
            override fun intervalRemoved(e: ListDataEvent) {
              removeIfNeeded()
            }

            private fun removeIfNeeded() {
              if (RecentProjectListActionProvider.getInstance().getActions(addClearListItem = false, useGroups = true).isEmpty()) {
                this@FlatWelcomeScreen.remove(recentProjects)
                this@FlatWelcomeScreen.revalidate()
                this@FlatWelcomeScreen.repaint()
              }
            }

            override fun contentsChanged(e: ListDataEvent) {
              removeIfNeeded()
            }
          })
          projectsList.addFocusListener(object : FocusListener {
            override fun focusGained(e: FocusEvent) {
              projectsList.repaint()
            }

            override fun focusLost(e: FocusEvent) {
              projectsList.repaint()
            }
          })
        }
      }
      add(createBody(), BorderLayout.CENTER)
      dropTarget = DropTarget(this, object : DropTargetAdapter() {
        override fun dragEnter(e: DropTargetDragEvent) {
          setDnd(true)
        }

        override fun dragExit(e: DropTargetEvent) {
          setDnd(false)
        }

        override fun drop(e: DropTargetDropEvent) {
          setDnd(false)
          e.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE)
          val transferable = e.transferable
          val list = FileCopyPasteUtil.getFiles(transferable)
          if (list != null && list.size > 0) {
            frame.coroutineScope.launch {
              ProjectUtil.openOrImportFilesAsync(list, "WelcomeFrame")
            }
            e.dropComplete(true)
            return
          }
          e.dropComplete(false)
        }

        private fun setDnd(dnd: Boolean) {
          inDnd = dnd
          repaint()
        }
      })
      TouchbarActionCustomizations.setShowText(touchbarActions, true)
      Touchbar.setActions(this, touchbarActions)
    }

    override fun paint(g: Graphics) {
      super.paint(g)
      if (!inDnd) {
        return
      }

      val bounds = bounds
      g.color = JBUI.CurrentTheme.DragAndDrop.Area.BACKGROUND
      g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
      val backgroundBorder = JBUI.CurrentTheme.DragAndDrop.BORDER_COLOR
      g.color = backgroundBorder
      g.drawRect(bounds.x, bounds.y, bounds.width, bounds.height)
      g.drawRect(bounds.x + 1, bounds.y + 1, bounds.width - 2, bounds.height - 2)
      val foreground = JBUI.CurrentTheme.DragAndDrop.Area.FOREGROUND
      g.color = foreground

      val labelFont = StartupUiUtil.labelFont
      val font = labelFont.deriveFont(labelFont.size + 5.0f)
      val drop = IdeBundle.message("welcome.screen.drop.files.to.open.text")
      g.font = font

      val dropWidth = g.fontMetrics.stringWidth(drop)
      val dropHeight = g.fontMetrics.height
      g.drawString(drop, bounds.x + (bounds.width - dropWidth) / 2, (bounds.y + (bounds.height - dropHeight) * 0.45).toInt())
    }

    private fun createBody(): JComponent {
      val panel = NonOpaquePanel(BorderLayout())
      panel.add(WelcomeScreenComponentFactory.createLogo(), BorderLayout.NORTH)
      touchbarActions.removeAll()
      val actionPanel = createQuickStartActionPanel()
      panel.add(actionPanel, BorderLayout.CENTER)
      touchbarActions.addAll(actionPanel.actions)
      panel.add(createSettingsAndDocsPanel(frame), BorderLayout.SOUTH)
      return panel
    }

    private fun createSettingsAndDocsPanel(frame: FlatWelcomeFrame): JComponent {
      val panel: JPanel = NonOpaquePanel(BorderLayout())
      val toolbar = NonOpaquePanel()
      toolbar.layout = BoxLayout(toolbar, BoxLayout.X_AXIS)
      toolbar.add(WelcomeScreenComponentFactory.createErrorsLink(this))
      toolbar.add(WelcomeScreenComponentFactory.createEventLink(IdeBundle.message("action.Events"),
                                                                ApplicationManager.getApplication().messageBus.connect(frame.coroutineScope)))
      toolbar.add(WelcomeScreenComponentFactory.createActionLink(
        frame,
        IdeBundle.message("action.Anonymous.text.configure"),
        IdeActions.GROUP_WELCOME_SCREEN_CONFIGURE,
        AllIcons.General.GearPlain,
        UIUtil.findComponentOfType(frame.rootPane, JList::class.java))
      )
      toolbar.add(
        WelcomeScreenComponentFactory.createActionLink(frame, IdeBundle.message("action.GetHelp"), IdeActions.GROUP_WELCOME_SCREEN_DOC,
                                                       null, null))
      panel.add(toolbar, BorderLayout.EAST)
      panel.border = JBUI.Borders.empty(0, 0, 8, 11)
      return panel
    }

    private fun createQuickStartActionPanel(): ActionPanel {
      val presentationFactory = PresentationFactory()
      val quickStartGroup = ActionManager.getInstance().getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART) as ActionGroup
      val dataContext = DataManager.getInstance().getDataContext(this)
      val visibleActions = Utils.expandActionGroup(quickStartGroup, presentationFactory, dataContext,
                                                   ActionPlaces.WELCOME_SCREEN, ActionUiKind.NONE)

      val mainPanel = ActionPanel(MigLayout("ins 0, novisualpadding, gap 5, flowy", "push[pref!, center]push"))
      mainPanel.isOpaque = false
      val panel = object : JPanel(VerticalLayout(JBUI.scale(5))) {
        private var firstAction: Component? = null
        override fun add(comp: Component): Component {
          val cmp = super.add(comp)
          if (firstAction == null) {
            firstAction = cmp
          }
          return cmp
        }

        override fun addNotify() {
          super.addNotify()
          if (firstAction != null) {
            frame.onFirstActionShown(firstAction!!)
          }
        }
      }

      panel.isOpaque = false
      frame.extendActionsGroup(mainPanel)
      mainPanel.add(panel)
      for (action in visibleActions) {
        val presentation = presentationFactory.getPresentation(action)
        var text = presentation.text
        if (text != null && text.endsWith("...")) {
          text = text.substring(0, text.length - 3)
        }
        var icon = presentation.icon
        if (icon == null || icon.iconHeight != JBUIScale.scale(16) || icon.iconWidth != JBUIScale.scale(16)) {
          icon = if (icon == null) JBUIScale.scaleIcon(EmptyIcon.create(16)) else IconUtil.scale(icon, null, 16f / icon.iconWidth)
          icon = IconUtil.colorize(icon, JBColor(0x6e6e6e, 0xafb1b3))
        }
        val wrapper = when {
          action is ActionGroup && action is ActionsWithPanelProvider -> ActionGroupPanelWrapper.wrapGroups(action, this)
          else -> action
        }
        val link = ActionLink(text, icon, wrapper, null, ActionPlaces.WELCOME_SCREEN)
        link.isFocusable = false // don't allow focus, as the containing panel is going to be focusable
        link.setPaintUnderline(false)
        link.setNormalColor(WelcomeScreenUIManager.getLinkNormalColor())
        val button = JActionLinkPanel(link)
        button.border = JBUI.Borders.empty(8, 20)
        if (wrapper is WelcomePopupAction) {
          button.add(WelcomeScreenComponentFactory.createArrow(link), BorderLayout.EAST)
          TouchbarActionCustomizations.setComponent(wrapper, link)
        }
        WelcomeScreenFocusManager.installFocusable(
          frame,
          button,
          wrapper,
          KeyEvent.VK_DOWN,
          KeyEvent.VK_UP,
          UIUtil.findComponentOfType(frame.component, JList::class.java)
        )
        panel.add(button)
        mainPanel.addAction(wrapper)
      }
      return mainPanel
    }
  }
}

private fun createWelcomeMenuBar(frame: JFrame, parentCoroutineScope: CoroutineScope) {
  if (!FlatWelcomeFrame.USE_TABBED_WELCOME_SCREEN || !SystemInfoRt.isMac) {
    return
  }

  val mainMenuActionGroupProvider: suspend () -> ActionGroup = {
    val manager = serviceAsync<ActionManager>()
    DefaultActionGroup(manager.getAction(IdeActions.GROUP_FILE), manager.getAction(IdeActions.GROUP_HELP_MENU))
  }

  if (Menu.isJbScreenMenuEnabled()) {
    createMacMenuBar(coroutineScope = parentCoroutineScope.childScope(),
                     component = frame.rootPane,
                     frame = frame,
                     mainMenuActionGroupProvider = mainMenuActionGroupProvider)
  }
  else {
    frame.rootPane.jMenuBar = object : IdeJMenuBar(parentCoroutineScope.childScope(), frame) {
      override suspend fun getMainMenuActionGroup(): ActionGroup = mainMenuActionGroupProvider()
    }
  }
}