// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.DynamicBundle
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.runActivity
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx.Companion.withLazyActionManager
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory
import com.intellij.openapi.actionSystem.impl.PopupMenuPreloader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.impl.FrameInfoHelper.Companion.isFloatingMenuBarSupported
import com.intellij.openapi.wm.impl.ProjectFrameHelper.Companion.getFrameHelper
import com.intellij.openapi.wm.impl.status.ClockPanel
import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import com.intellij.ui.ScreenUtil
import com.intellij.ui.mac.foundation.NSDefaults
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.ui.mac.screenmenu.MenuBar
import com.intellij.ui.plaf.beg.IdeaMenuUI
import com.intellij.util.Alarm
import com.intellij.util.IJSwingUtilities
import com.intellij.util.childScope
import com.intellij.util.ui.*
import kotlinx.coroutines.*
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.AffineTransform
import java.awt.geom.GeneralPath
import java.awt.geom.RoundRectangle2D
import javax.swing.*
import javax.swing.border.Border
import kotlin.math.cos
import kotlin.math.sqrt

@Suppress("LeakingThis")
open class IdeMenuBar internal constructor() : JMenuBar(), IdeEventQueue.EventDispatcher, UISettingsListener {
  enum class State {
    EXPANDED,
    COLLAPSING,
    COLLAPSED,
    EXPANDING,
    TEMPORARY_EXPANDED;

    val isInProgress: Boolean
      get() = this == COLLAPSING || this == EXPANDING
  }

  private var visibleActions = ArrayList<AnAction>()
  private val presentationFactory = MenuItemPresentationFactory()
  private val timerListener = MyTimerListener()

  @Suppress("DEPRECATION")
  protected val coroutineScope = ApplicationManager.getApplication().coroutineScope.childScope()

  private var clockPanel: ClockPanel? = null
  private var button: MyExitFullScreenButton? = null
  private var animator: MyAnimator? = null
  private var activationWatcher: Timer? = null
  private val updateAlarm = Alarm()
  private var state = State.EXPANDED
  private var progress = 0.0
  private var activated = false
  private var screenMenuPeer: MenuBar? = null

  init {
    if (isFloatingMenuBarSupported) {
      animator = MyAnimator()
      activationWatcher = TimerUtil.createNamedTimer("IdeMenuBar", 100, MyActionListener())
      clockPanel = ClockPanel()
      button = MyExitFullScreenButton()
      add(clockPanel)
      add(button)
      addPropertyChangeListener(IdeFrameDecorator.FULL_SCREEN) { updateState() }
      addMouseListener(MyMouseListener())
    }
    else {
      animator = null
      activationWatcher = null
      clockPanel = null
      button = null
    }
    if (IdeFrameDecorator.isCustomDecorationActive()) {
      isOpaque = false
    }
  }

  companion object {
    fun createMenuBar(): IdeMenuBar {
      return if (SystemInfoRt.isLinux) LinuxIdeMenuBar() else IdeMenuBar()
    }

    fun installAppMenuIfNeeded(frame: JFrame) {
      val menuBar = frame.jMenuBar
      // must be called when frame is visible (otherwise frame.getPeer() == null)
      if (menuBar is IdeMenuBar) {
        try {
          menuBar.doInstallAppMenuIfNeeded(frame)
        }
        catch (e: Throwable) {
          LOG.warn("cannot install app menu", e)
        }
      }
      else if (menuBar != null) {
        LOG.info("The menu bar '$menuBar of frame '$frame' isn't instance of IdeMenuBar")
      }
    }
  }

  // JMenuBar calls getBorder on init before our own init (super is called before our constructor).
  fun getState(): State {
    return state
  }

  fun setState(state: State) {
    this.state = state
    val activationWatcher = activationWatcher ?: return
    if (state == State.EXPANDING && !activationWatcher.isRunning) {
      activationWatcher.start()
    }
    else if (activationWatcher.isRunning && (state == State.EXPANDED || state == State.COLLAPSED)) {
      activationWatcher.stop()
    }
  }

  override fun add(menu: JMenu): JMenu {
    menu.isFocusable = false
    return super.add(menu)
  }

  override fun getBorder(): Border? {
    if (IdeFrameDecorator.isCustomDecorationActive()) {
      return JBUI.Borders.empty()
    }

    val state = state
    // avoid moving lines
    if (state == State.EXPANDING || state == State.COLLAPSING) {
      return JBUI.Borders.empty()
    }

    // fix for a Darcula double border
    if (state == State.TEMPORARY_EXPANDED && StartupUiUtil.isUnderDarcula) {
      return JBUI.Borders.customLine(Gray._75, 0, 0, 1, 0)
    }

    // save 1px for mouse handler
    if (state == State.COLLAPSED) {
      return JBUI.Borders.emptyBottom(1)
    }

    val uiSettings = UISettings.getInstance()
    return if (uiSettings.showMainToolbar || uiSettings.showNavigationBar) super.getBorder() else null
  }

  override fun paint(g: Graphics) {
    // otherwise, there will be a 1px line on top
    if (state == State.COLLAPSED) {
      return
    }

    super.paint(g)
  }

  override fun doLayout() {
    super.doLayout()

    val clockPanel = clockPanel ?: return
    val button = button ?: return
    if (state == State.EXPANDED) {
      clockPanel.isVisible = false
      button.isVisible = false
    }
    else {
      clockPanel.isVisible = true
      button.isVisible = true
      var preferredSize = button.preferredSize
      button.setBounds(bounds.width - preferredSize.width, 0, preferredSize.width, preferredSize.height)
      preferredSize = clockPanel.preferredSize
      clockPanel.setBounds(bounds.width - preferredSize.width - button.width, 0, preferredSize.width, preferredSize.height)
    }
  }

  override fun menuSelectionChanged(isIncluded: Boolean) {
    if (!isIncluded && state == State.TEMPORARY_EXPANDED) {
      activated = false
      state = State.COLLAPSING
      restartAnimator()
      return
    }

    if (isIncluded && state == State.COLLAPSED) {
      activated = true
      state = State.TEMPORARY_EXPANDED
      revalidate()
      repaint()
      SwingUtilities.invokeLater {
        val menu = getMenu(selectionModel.selectedIndex)
        if (menu.isPopupMenuVisible) {
          menu.isPopupMenuVisible = false
          menu.isPopupMenuVisible = true
        }
      }
    }
    super.menuSelectionChanged(isIncluded)
  }

  private val isActivated: Boolean
    get() {
      val index = selectionModel.selectedIndex
      return index != -1 && getMenu(index).isPopupMenuVisible
    }

  private fun updateState() {
    val animator = animator ?: return
    val window = (SwingUtilities.getWindowAncestor(this) as? IdeFrame) ?: return
    val fullScreen = window.isInFullScreen
    if (fullScreen) {
      state = State.COLLAPSING
      restartAnimator()
    }
    else {
      animator.suspend()
      state = State.EXPANDED
      clockPanel?.let { clockPanel ->
        clockPanel.isVisible = false
        button?.isVisible = false
      }
    }
  }

  override fun getPreferredSize(): Dimension {
    val dimension = super.getPreferredSize()
    if (state.isInProgress) {
      dimension.height = COLLAPSED_HEIGHT +
                         ((if (state == State.COLLAPSING) 1 - progress else progress) * (dimension.height - COLLAPSED_HEIGHT)).toInt()
    }
    else if (state == State.COLLAPSED) {
      dimension.height = COLLAPSED_HEIGHT
    }
    return dimension
  }

  private fun restartAnimator() {
    val animator = animator ?: return
    animator.reset()
    animator.resume()
  }

  override fun addNotify() {
    super.addNotify()
    val activity = StartUpMeasurer.startActivity("ide menu bar init")
    val screenMenuPeer: MenuBar?
    if (Menu.isJbScreenMenuEnabled()) {
      screenMenuPeer = MenuBar("MainMenu")
      this.screenMenuPeer = screenMenuPeer
      screenMenuPeer.setFrame(SwingUtilities.getWindowAncestor(this))
    }
    else {
      screenMenuPeer = null
    }

    coroutineScope.launch {
      val app = ApplicationManager.getApplication()
      launch {
        app.serviceAsync<CustomActionsSchema>()
      }

      val actionManager = app.serviceAsync<ActionManager>()
      updateActions(actionManager = actionManager, screenMenuPeer = screenMenuPeer)
    }
    activity.end()

    IdeEventQueue.getInstance().addDispatcher(dispatcher = this, scope = coroutineScope)
  }

  private suspend fun updateActions(actionManager: ActionManager, screenMenuPeer: MenuBar?) {
    runActivity("ide menu bar actions init") {
      val mainActionGroup = getMainMenuActionGroup()
      withContext(Dispatchers.EDT) {
        val actions = doUpdateMenuActions(mainActionGroup = mainActionGroup,
                                          forceRebuild = false,
                                          manager = actionManager,
                                          screenMenuPeer = screenMenuPeer)
        for (action in actions) {
          if (action is ActionGroup) {
            PopupMenuPreloader.install(this@IdeMenuBar, ActionPlaces.MAIN_MENU, null) { action }
          }
        }
      }

      actionManager.addTimerListener(timerListener)
      coroutineScope.coroutineContext.job.invokeOnCompletion {
        actionManager.removeTimerListener(timerListener)
      }
    }
  }

  override fun removeNotify() {
    screenMenuPeer?.let {
      @Suppress("SSBasedInspection")
      it.dispose()
      screenMenuPeer = null
    }
    if (ScreenUtil.isStandardAddRemoveNotify(this)) {
      animator?.suspend()
      coroutineScope.cancel()
    }
    super.removeNotify()
  }

  override fun uiSettingsChanged(uiSettings: UISettings) {
    updateAlarm.cancelAllRequests()
    updateAlarm.addRequest({
                             presentationFactory.reset()
                             updateMenuActions(true)
                           }, 50)
  }

  override fun dispatch(e: AWTEvent): Boolean {
    if (e is MouseEvent && state != State.EXPANDED /*&& !myState.isInProgress()*/) {
      considerRestartingAnimator(e)
    }
    return false
  }

  private fun considerRestartingAnimator(mouseEvent: MouseEvent) {
    var mouseInside = activated || UIUtil.isDescendingFrom(findActualComponent(mouseEvent), this)
    if (mouseEvent.id == MouseEvent.MOUSE_EXITED && mouseEvent.source === SwingUtilities.windowForComponent(this) && !activated) {
      mouseInside = false
    }
    if (mouseInside && state == State.COLLAPSED) {
      state = State.EXPANDING
      restartAnimator()
    }
    else if (!mouseInside && state != State.COLLAPSING && state != State.COLLAPSED) {
      state = State.COLLAPSING
      restartAnimator()
    }
  }

  private fun findActualComponent(mouseEvent: MouseEvent): Component? {
    var component: Component? = mouseEvent.component ?: return null
    val deepestComponent = if (state != State.EXPANDED &&
                               !state.isInProgress &&
                               contains(SwingUtilities.convertPoint(component, mouseEvent.point, this))) {
      this
    }
    else {
      SwingUtilities.getDeepestComponentAt(mouseEvent.component, mouseEvent.x, mouseEvent.y)
    }
    if (deepestComponent != null) {
      component = deepestComponent
    }
    return component
  }

  @JvmOverloads
  fun updateMenuActions(forceRebuild: Boolean = false) {
    doUpdateMenuActions(mainActionGroup = getMainMenuActionGroup(),
                        forceRebuild = forceRebuild,
                        manager = ActionManager.getInstance(),
                        screenMenuPeer = screenMenuPeer)
  }

  protected fun updateMenuActionsLazily() {
    withLazyActionManager(coroutineScope) { manager ->
      doUpdateMenuActions(mainActionGroup = getMainMenuActionGroup(), forceRebuild = true, manager = manager, screenMenuPeer = screenMenuPeer)
    }
  }

  private fun doUpdateMenuActions(mainActionGroup: ActionGroup?, forceRebuild: Boolean, manager: ActionManager, screenMenuPeer: MenuBar?): List<AnAction> {
    val enableMnemonics = !UISettings.getInstance().disableMnemonics
    val newVisibleActions = ArrayList<AnAction>()
    val targetComponent = IJSwingUtilities.getFocusedComponentInWindowOrSelf(this)
    val dataContext = DataManager.getInstance().getDataContext(targetComponent)
    if (mainActionGroup != null) {
      expandActionGroup(mainActionGroup = mainActionGroup,
                        context = dataContext,
                        newVisibleActions = newVisibleActions,
                        actionManager = manager)
    }
    if (!forceRebuild && !presentationFactory.isNeedRebuild && newVisibleActions == visibleActions) {
      for (child in components) {
        if (child is ActionMenu) {
          child.updateFromPresentation(enableMnemonics)
        }
      }
      return newVisibleActions
    }

    // should rebuild UI
    val changeBarVisibility = newVisibleActions.isEmpty() || visibleActions.isEmpty()
    visibleActions = newVisibleActions
    removeAll()
    screenMenuPeer?.beginFill()
    val isDarkMenu = isDarkMenu
    for (action in newVisibleActions) {
      val actionMenu = ActionMenu(null, ActionPlaces.MAIN_MENU, (action as ActionGroup), presentationFactory, enableMnemonics, isDarkMenu,
                                  true)
      if (IdeFrameDecorator.isCustomDecorationActive()) {
        actionMenu.isOpaque = false
        actionMenu.isFocusable = false
      }
      if (screenMenuPeer != null) {
        screenMenuPeer.add(actionMenu.screenMenuPeer)
      }
      else {
        add(actionMenu)
      }
    }
    presentationFactory.resetNeedRebuild()
    screenMenuPeer?.endFill()
    updateAppMenu()
    updateGlobalMenuRoots()
    if (clockPanel != null) {
      add(clockPanel)
      add(button)
    }
    validate()
    if (changeBarVisibility) {
      invalidate()
      (SwingUtilities.getAncestorOfClass(JFrame::class.java, this) as JFrame?)?.validate()
    }
    return newVisibleActions
  }

  protected open val isDarkMenu: Boolean
    get() = SystemInfo.isMacSystemMenu && NSDefaults.isDarkMenuBar()

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    paintBackground(g)
  }

  protected fun paintBackground(g: Graphics) {
    if (IdeFrameDecorator.isCustomDecorationActive()) {
      val window = SwingUtilities.getWindowAncestor(this)
      if (window is IdeFrame) {
        val fullScreen = (window as IdeFrame).isInFullScreen
        if (!fullScreen) {
          return
        }
      }
    }
    if (StartupUiUtil.isUnderDarcula || UIUtil.isUnderIntelliJLaF()) {
      g.color = IdeaMenuUI.getMenuBackgroundColor()
      g.fillRect(0, 0, width, height)
    }
  }

  override fun paintChildren(g: Graphics) {
    if (state.isInProgress) {
      val g2 = g as Graphics2D
      val oldTransform = g2.transform
      val newTransform = if (oldTransform == null) AffineTransform() else AffineTransform(oldTransform)
      newTransform.concatenate(AffineTransform.getTranslateInstance(0.0, (height - super.getPreferredSize().height).toDouble()))
      g2.transform = newTransform
      super.paintChildren(g2)
      g2.transform = oldTransform
    }
    else if (state != State.COLLAPSED) {
      super.paintChildren(g)
    }
  }

  private fun expandActionGroup(mainActionGroup: ActionGroup, context: DataContext, newVisibleActions: MutableList<in AnAction>, actionManager: ActionManager) {
    // the only code that does not reuse ActionUpdater (do not repeat that anywhere else)
    val children = mainActionGroup.getChildren(null)
    for (action in children) {
      if (action !is ActionGroup) {
        continue
      }

      val presentation = presentationFactory.getPresentation(action)
      val e = AnActionEvent(null, context, ActionPlaces.MAIN_MENU, presentation, actionManager, 0)
      ActionUtil.performDumbAwareUpdate(action, e, false)
      if (presentation.isVisible) {
        newVisibleActions.add(action)
      }
    }
  }

  open fun getMainMenuActionGroup(): ActionGroup? {
    val rootPane = rootPane
    val group = if (rootPane is IdeRootPane) rootPane.mainMenuActionGroup else null
    return group ?: CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_MAIN_MENU) as ActionGroup?
  }

  override fun getMenuCount(): Int {
    val menuCount = super.getMenuCount()
    return if (clockPanel == null) menuCount else menuCount - 2
  }

  protected open fun updateGlobalMenuRoots() {}

  private inner class MyTimerListener : TimerListener {
    override fun getModalityState(): ModalityState = ModalityState.stateForComponent(this@IdeMenuBar)

    override fun run() {
      if (!isShowing) {
        return
      }

      val w = SwingUtilities.windowForComponent(this@IdeMenuBar)
      if (w != null && !w.isActive) {
        return
      }

      // do not update when a popup menu is shown
      // (if a popup menu contains action which is also in the menu bar, it should not be enabled/disabled)
      val menuSelectionManager = MenuSelectionManager.defaultManager()
      val selectedPath = menuSelectionManager.selectedPath
      if (selectedPath.isNotEmpty()) {
        return
      }

      // don't update toolbar if there is currently active modal dialog
      val window = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
      if (window is Dialog && window.isModal) {
        return
      }
      updateMenuActions()
    }
  }

  private inner class MyAnimator : Animator("MenuBarAnimator", 16, 300, false) {
    override fun paintNow(frame: Int, totalFrames: Int, cycle: Int) {
      progress = (1 - cos(Math.PI * (frame.toFloat() / totalFrames))) / 2
      revalidate()
      repaint()
    }

    override fun paintCycleEnd() {
      progress = 1.0
      when (state) {
        State.COLLAPSING -> state = State.COLLAPSED
        State.EXPANDING -> state = State.TEMPORARY_EXPANDED
        else -> {}
      }
      if (!isShowing) {
        return
      }

      revalidate()
      if (state == State.COLLAPSED) {
        // we should repaint the parent, to clear 1px on top when a menu is collapsed
        parent.repaint()
      }
      else {
        repaint()
      }
    }
  }

  private inner class MyActionListener : ActionListener {
    override fun actionPerformed(e: ActionEvent) {
      if (state == State.EXPANDED || state == State.EXPANDING) {
        return
      }

      val activated: Boolean = isActivated
      if (this@IdeMenuBar.activated && !activated && state == State.TEMPORARY_EXPANDED) {
        this@IdeMenuBar.activated = false
        state = State.COLLAPSING
        restartAnimator()
      }
      if (activated) {
        this@IdeMenuBar.activated = true
      }
    }
  }

  protected open fun doInstallAppMenuIfNeeded(frame: JFrame) {}

  open fun onToggleFullScreen(isFullScreen: Boolean) {}
}

private val LOG = logger<IdeMenuBar>()
private const val COLLAPSED_HEIGHT = 2

private class MyMouseListener : MouseAdapter() {
  override fun mousePressed(e: MouseEvent) {
    val c = e.component
    if (c !is IdeMenuBar) {
      return
    }

    val size = c.getSize()
    val insets = c.insets
    val p = e.point
    if (p.y < insets.top || p.y >= size.height - insets.bottom) {
      val item = c.findComponentAt(p.x, size.height / 2)
      if (item is JMenuItem) {
        // re-target border clicks as a menu item ones
        item.dispatchEvent(MouseEventAdapter.convert(e, item, 1, 1))
        e.consume()
      }
    }
  }
}

// NOTE: for OSX only
private fun updateAppMenu() {
  if (!Menu.isJbScreenMenuEnabled()) {
    return
  }

  // 1. rename with localized
  Menu.renameAppMenuItems(DynamicBundle(IdeMenuBar::class.java, "messages.MacAppMenuBundle"))

  //
  // 2. add custom new items in AppMenu
  //

  //Example (add new item after "Preferences"):
  //int pos = appMenu.findIndexByTitle("Pref.*");
  //int pos2 = appMenu.findIndexByTitle("NewCustomItem");
  //if (pos2 < 0) {
  //  MenuItem mi = new MenuItem();
  //  mi.setLabel("NewCustomItem", null);
  //  mi.setActionDelegate(() -> System.err.println("NewCustomItem executes"));
  //  appMenu.add(mi, pos, true);
  //}
}

private class MyExitFullScreenButton : JButton() {
  init {
    isFocusable = false
    addActionListener {
      getFrameHelper(SwingUtilities.getWindowAncestor(this))?.toggleFullScreen(false)
    }
    addMouseListener(object : MouseAdapter() {
      override fun mouseEntered(e: MouseEvent) {
        model.isRollover = true
      }

      override fun mouseExited(e: MouseEvent) {
        model.isRollover = false
      }
    })
  }

  override fun getPreferredSize(): Dimension {
    val height: Int
    val parent = parent
    height = if (isVisible && parent != null) {
      parent.size.height - parent.insets.top - parent.insets.bottom
    }
    else {
      super.getPreferredSize().height
    }
    return Dimension(height, height)
  }

  override fun getMaximumSize(): Dimension = preferredSize

  override fun paint(g: Graphics) {
    val g2d = g.create() as Graphics2D
    try {
      g2d.color = UIManager.getColor("Label.background")
      g2d.fillRect(0, 0, width + 1, height + 1)
      g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2d.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY)
      val s = height.toDouble() / 13
      g2d.translate(s, s)
      val plate: Shape = RoundRectangle2D.Double(0.0, 0.0, s * 11, s * 11, s, s)
      val color = UIManager.getColor("Label.foreground")
      val hover = model.isRollover || model.isPressed
      g2d.color = ColorUtil.withAlpha(color, if (hover) .25 else .18)
      g2d.fill(plate)
      g2d.color = ColorUtil.withAlpha(color, if (hover) .4 else .33)
      g2d.draw(plate)
      g2d.color = ColorUtil.withAlpha(color, if (hover) .7 else .66)
      var path = GeneralPath()
      path.moveTo(s * 2, s * 6)
      path.lineTo(s * 5, s * 6)
      path.lineTo(s * 5, s * 9)
      path.lineTo(s * 4, s * 8)
      path.lineTo(s * 2, s * 10)
      path.quadTo(s * 2 - s / sqrt(2.0), s * 9 + s / sqrt(2.0), s, s * 9)
      path.lineTo(s * 3, s * 7)
      path.lineTo(s * 2, s * 6)
      path.closePath()
      g2d.fill(path)
      g2d.draw(path)
      path = GeneralPath()
      path.moveTo(s * 6, s * 2)
      path.lineTo(s * 6, s * 5)
      path.lineTo(s * 9, s * 5)
      path.lineTo(s * 8, s * 4)
      path.lineTo(s * 10, s * 2)
      path.quadTo(s * 9 + s / sqrt(2.0), s * 2 - s / sqrt(2.0), s * 9, s)
      path.lineTo(s * 7, s * 3)
      path.lineTo(s * 6, s * 2)
      path.closePath()
      g2d.fill(path)
      g2d.draw(path)
    }
    finally {
      g2d.dispose()
    }
  }
}

