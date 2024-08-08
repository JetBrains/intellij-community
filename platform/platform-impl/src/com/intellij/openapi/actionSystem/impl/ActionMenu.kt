// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.diagnostic.UILatencyLogger
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.IdeEventQueue.Companion.getInstance
import com.intellij.ide.ui.UISettings
import com.intellij.internal.inspector.UiInspectorActionUtil
import com.intellij.internal.inspector.UiInspectorContextProvider
import com.intellij.internal.inspector.UiInspectorUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.MainMenuPresentationAware
import com.intellij.openapi.actionSystem.impl.ActionPresentationDecorator.decorateTextIfNeeded
import com.intellij.openapi.actionSystem.impl.actionholder.createActionRef
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IconLoader.getDarkIcon
import com.intellij.openapi.util.IconLoader.getDisabledIcon
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.ui.ColorUtil
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBMenu
import com.intellij.ui.icons.getMenuBarIcon
import com.intellij.ui.mac.foundation.NSDefaults
import com.intellij.ui.plaf.beg.BegMenuItemUI
import com.intellij.ui.plaf.beg.IdeaMenuUI
import com.intellij.util.FontUtil
import com.intellij.util.ReflectionUtil
import com.intellij.util.SingleAlarm
import com.intellij.util.concurrency.EdtScheduler
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Job
import java.awt.*
import java.awt.event.AWTEventListener
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.event.MenuEvent
import javax.swing.event.MenuListener
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

@Suppress("RedundantConstructorKeyword")
class ActionMenu constructor(private val context: DataContext?,
                             private val place: String,
                             group: ActionGroup,
                             private val presentationFactory: PresentationFactory,
                             private var isMnemonicEnabled: Boolean,
                             private val useDarkIcons: Boolean,
                             val isHeaderMenuItem: Boolean = false) : JBMenu() {
  private val group = createActionRef(group)
  private val presentation = presentationFactory.getPresentation(group)

  private var disposable: Disposable? = null
  private val subElementSelector = if (SubElementSelector.isForceDisabled) null else SubElementSelector(this)
  val anAction: AnAction
    get() = group.getAction()

  private var specialMenu: JPopupMenu? = null

  internal var isTryingToShowPopupMenu = false
    private set

  override fun removeNotify() {
    super.removeNotify()
    if (disposable != null) {
      Disposer.dispose(disposable!!)
      disposable = null
    }
  }

  init {
    updateUI()

    init()

    // also triggering initialization of private field "popupMenu" from JMenu with our own JBPopupMenu
    BegMenuItemUI.registerMultiChoiceSupport(getPopupMenu()) { popupMenu ->
      Utils.updateMenuItems(popupMenu, getDataContext(), this.place, this.presentationFactory)
    }
  }

  companion object {
    @Deprecated("Use ActionUtil.SUPPRESS_SUBMENU")
    @JvmField
    val SUPPRESS_SUBMENU = ActionUtil.SUPPRESS_SUBMENU

    @Deprecated("Use ActionUtil.ALWAYS_VISIBLE_GROUP")
    @JvmField
    val ALWAYS_VISIBLE = ActionUtil.ALWAYS_VISIBLE_GROUP

    @Deprecated("Use ActionUtil.KEYBOARD_SHORTCUT_SUFFIX")
    @JvmField
    val KEYBOARD_SHORTCUT_SUFFIX = ActionUtil.KEYBOARD_SHORTCUT_SUFFIX

    @Deprecated("Use ActionUtil.SECONDARY_ICON")
    @JvmField
    val SECONDARY_ICON = ActionUtil.SECONDARY_ICON

    @JvmStatic
    fun shouldConvertIconToDarkVariant(): Boolean {
      return JBColor.isBright() && ColorUtil.isDark(JBColor.namedColor("MenuItem.background", 0xffffff))
    }

    @JvmStatic
    val isShowNoIcons: Boolean
      get() {
        return SystemInfoRt.isMac && (ExperimentalUI.isNewUI() ||
                                      Registry.get("ide.macos.main.menu.alignment.options").isOptionEnabled("No icons"))
      }

    @JvmStatic
    fun isShowNoIcons(action: AnAction?): Boolean {
      return when {
        action == null -> false
        action is MainMenuPresentationAware && (action as MainMenuPresentationAware).alwaysShowIconInMainMenu() -> false
        else -> isShowNoIcons
      }
    }

    @JvmStatic
    val isAligned: Boolean
      get() = SystemInfoRt.isMac && Registry.get("ide.macos.main.menu.alignment.options").isOptionEnabled("Aligned")

    @JvmStatic
    val isAlignedInGroup: Boolean
      get() = SystemInfoRt.isMac && Registry.get("ide.macos.main.menu.alignment.options").isOptionEnabled("Aligned in group")

    @JvmStatic
    fun showDescriptionInStatusBar(isIncluded: Boolean, component: Component?, description: @NlsContexts.StatusBarText String?) {
      val frame = (if (component is IdeFrame) component
      else SwingUtilities.getAncestorOfClass(IdeFrame::class.java, component)) as? IdeFrame
      frame?.getStatusBar()?.setInfo(if (isIncluded) description else null)
    }
  }

  override fun getPopupMenu(): JPopupMenu {
    var specialMenu = specialMenu
    if (specialMenu == null) {
      specialMenu = JBPopupMenu()
      this.specialMenu = specialMenu
      specialMenu.setInvoker(this)
      popupListener = createWinListener(specialMenu)
      ReflectionUtil.setField(JMenu::class.java, this, JPopupMenu::class.java, "popupMenu", specialMenu)
      UiInspectorUtil.registerProvider(specialMenu, UiInspectorContextProvider {
        UiInspectorActionUtil.collectActionGroupInfo("Menu", group.getAction(), place, presentationFactory)
      })
    }
    return super.getPopupMenu()
  }

  override fun updateUI() {
    // null myPlace means that Swing calls updateUI before our constructor
    @Suppress("SENSELESS_COMPARISON")
    if (place == null) {
      return
    }

    setUI(IdeaMenuUI.createUI(this))
    setFont(FontUtil.getMenuFont())
    getPopupMenu().updateUI()
  }

  private fun init() {
    subElementSelector?.stubItem = if (SystemInfo.isMacSystemMenu && isMainMenuPlace) null else StubItem()
    addStubItem()
    setBorderPainted(false)
    val menuListener = MenuListenerImpl()
    addMenuListener(menuListener)
    getModel().addChangeListener(menuListener)
    updateFromPresentation(isMnemonicEnabled)
  }

  val isMainMenuPlace: Boolean
    get() = place == ActionPlaces.MAIN_MENU

  internal fun updateFromPresentation(enableMnemonics: Boolean) {
    isMnemonicEnabled = enableMnemonics
    isVisible = presentation.isVisible
    setEnabled(presentation.isEnabled)
    setText(decorateTextIfNeeded(anAction, presentation.getText(isMnemonicEnabled)))
    mnemonic = presentation.getMnemonic()
    displayedMnemonicIndex = presentation.getDisplayedMnemonicIndex()
    updateIcon()
  }

  private fun addStubItem() {
    subElementSelector?.stubItem?.let {
      add(it)
    }
  }

  override fun setDisplayedMnemonicIndex(index: Int) {
    super.setDisplayedMnemonicIndex(if (isMnemonicEnabled) index else -1)
  }

  override fun setMnemonic(mnemonic: Int) {
    super.setMnemonic(if (isMnemonicEnabled) mnemonic else 0)
  }

  private fun updateIcon() {
    if (anAction is Toggleable && Toggleable.isSelected(presentation)) {
      setToggledIcon()
      return
    }
    var icon = presentation.icon ?: return
    if (!UISettings.getInstance().showIconsInMenus) {
      return
    }

    if (SystemInfo.isMacSystemMenu && ActionPlaces.MAIN_MENU == place) {
      // JDK can't correctly paint our HiDPI icons at the system menu bar
      icon = getMenuBarIcon(icon, useDarkIcons)
    }
    else if (shouldConvertIconToDarkVariant()) {
      icon = getDarkIcon(icon, true)
    }

    if (isShowNoIcons &&
        !(group.getAction() is MainMenuPresentationAware && (group.getAction() as MainMenuPresentationAware).alwaysShowIconInMainMenu())) {
        setIcon(null)
        setDisabledIcon(null)
    }
    else {
      setIcon(icon)
      val presentationDisabledIcon = presentation.disabledIcon
      setDisabledIcon(presentationDisabledIcon ?: getDisabledIcon(icon))
    }
  }

  override fun menuSelectionChanged(isIncluded: Boolean) {
    super.menuSelectionChanged(isIncluded)
    showDescriptionInStatusBar(isIncluded = isIncluded, component = this, description = presentation.description)
  }

  override fun processMouseEvent(e: MouseEvent) {
    var shouldCancelIgnoringOfNextSelectionRequest = false
    val subElementSelector = subElementSelector
    if (subElementSelector != null) {
      when (e.id) {
        MouseEvent.MOUSE_PRESSED -> {
          subElementSelector.ignoreNextSelectionRequest()
          shouldCancelIgnoringOfNextSelectionRequest = true
        }
        MouseEvent.MOUSE_ENTERED -> subElementSelector.ignoreNextSelectionRequest(delay * 2)
      }
    }

    try {
      super.processMouseEvent(e)
    }
    finally {
      if (shouldCancelIgnoringOfNextSelectionRequest) {
        subElementSelector?.cancelIgnoringOfNextSelectionRequest()
      }
    }
  }

  private inner class MenuListenerImpl : ChangeListener, MenuListener {
    var delayedClear: Job? = null
    var isSelected: Boolean = false

    override fun stateChanged(e: ChangeEvent) {
      // Re-implement javax.swing.JMenu.MenuChangeListener to avoid recursive event notifications
      // if 'menuSelected' fires unrelated 'stateChanged' event, without changing 'model.isSelected()' value.
      val model = e.source as ButtonModel
      val modelSelected = model.isSelected
      if (modelSelected != isSelected) {
        isSelected = modelSelected
        if (modelSelected) {
          menuSelected()
        }
        else {
          menuDeselected()
        }
      }
    }

    override fun menuCanceled(e: MenuEvent) {
      onMenuHidden()
    }

    override fun menuDeselected(e: MenuEvent) {
      // Use ChangeListener instead to guard against recursive calls
    }

    override fun menuSelected(e: MenuEvent) {
      // Use ChangeListener instead to guard against recursive calls
    }

    private fun menuDeselected() {
      if (disposable != null) {
        Disposer.dispose(disposable!!)
        disposable = null
      }
      onMenuHidden()
      subElementSelector?.cancelNextSelection()
    }

    private fun onMenuHidden() {
      val clearSelf = Runnable {
        clearItems()
        addStubItem()
      }

      if (SystemInfo.isMacSystemMenu && isMainMenuPlace) {
        // Menu items may contain mnemonic, and they can affect key-event dispatching (when Alt pressed)
        // To avoid the influence of mnemonic it's necessary to clear items when a menu was hidden.
        // When a user selects item of a system menu (under macOS), AppKit generates such sequence: CloseParentMenu -> PerformItemAction
        // So we can destroy menu-item before item's action performed, and because of that action will not be executed.
        // Defer clearing to avoid this problem.
        delayedClear = EdtScheduler.getInstance().schedule(1.seconds, clearSelf)
      }
      else {
        clearSelf.run()
      }
    }

    private fun menuSelected() {
      if (!isShowing) {
        // Not needed for hidden menu and leads to disposable leak for detached menu items
        return
      }

      val startMs = System.currentTimeMillis()
      val helper = UsabilityHelper(this@ActionMenu)
      if (disposable == null) {
        disposable = Disposer.newDisposable()
      }
      Disposer.register(disposable!!, helper)
      if (delayedClear != null) {
        delayedClear!!.cancel()
        delayedClear = null
        clearItems()
      }
      if (SystemInfo.isMacSystemMenu && ActionPlaces.MAIN_MENU == place) {
        fillMenu()
        // NOTE: FUS for OSX system menu is implemented in MacNativeActionMenu
      } else {
        UILatencyLogger.MAIN_MENU_LATENCY.log(System.currentTimeMillis() - startMs)
      }
    }
  }

  override fun setPopupMenuVisible(value: Boolean) {
    isTryingToShowPopupMenu = value
    if (value && !(SystemInfo.isMacSystemMenu && ActionPlaces.MAIN_MENU == place)) {
      fillMenu()
      if (!isSelected) {
        return
      }
    }

    super.setPopupMenuVisible(value)

    if (value) {
      subElementSelector?.selectSubElementIfNecessary()
    }
  }

  fun clearItems() {
    if (SystemInfo.isMacSystemMenu && isMainMenuPlace) {
      for (menuComponent in getMenuComponents()) {
        if (menuComponent is ActionMenu) {
          menuComponent.clearItems()
        }
        else if (menuComponent is ActionMenuItem) {
          // Looks like an old-fashioned ugly workaround
          // JDK 1.7 on Mac works wrong with such functional keys
          if (!SystemInfoRt.isMac) {
            menuComponent.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F24, 0))
          }
        }
      }
    }
    removeAll()
    validate()
  }

  private fun getDataContext(): DataContext {
    var context = context
    if (context != null) {
      return context
    }

    val dataManager = DataManager.getInstance()
    @Suppress("DEPRECATION")
    context = dataManager.getDataContext()
    if (PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(context) == null) {
      val frame = ComponentUtil.getParentOfType(IdeFrame::class.java, this)
      context = dataManager.getDataContext(IdeFocusManager.getGlobalInstance().getLastFocusedFor(frame as Window?))
    }
    return context
  }

  fun fillMenu() {
    val context = getDataContext()
    val isDarkMenu = SystemInfo.isMacSystemMenu && NSDefaults.isDarkMenuBar()
    Utils.fillMenu(group = group.getAction(),
                   component = this,
                   nativePeer = null,
                   enableMnemonics = isMnemonicEnabled,
                   presentationFactory = presentationFactory,
                   context = context,
                   place = place,
                   isWindowMenu = true,
                   useDarkIcons = isDarkMenu,
                   progressPoint = RelativePoint.getNorthEastOf(this)) { !isSelected }
  }
}

private class UsabilityHelper(component: Component) : IdeEventQueue.EventDispatcher, AWTEventListener, Disposable {
  private var component: Component?
  private var startMousePoint: Point?
  private var xClosestToTargetSoFar = 0
  private var closestHorizontalDistanceSoFar = 0
  private var upperTargetPoint: Point? = null
  private var lowerTargetPoint: Point? = null
  private var callbackAlarm: SingleAlarm? = null
  private var eventToRedispatch: MouseEvent? = null

  init {
    callbackAlarm = SingleAlarm(
      task = {
        Disposer.dispose(callbackAlarm!!)
        callbackAlarm = null
        if (eventToRedispatch != null) {
          getInstance().dispatchEvent(eventToRedispatch!!)
        }
      },
      delay = 50,
      parentDisposable = this,
      modalityState = ModalityState.any(),
    )
    this.component = component
    val info = MouseInfo.getPointerInfo()
    startMousePoint = info?.location
    if (startMousePoint != null) {
      xClosestToTargetSoFar = startMousePoint!!.x
      Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.COMPONENT_EVENT_MASK)
      getInstance().addDispatcher(this, this)
    }
  }

  override fun eventDispatched(event: AWTEvent) {
    if (event !is ComponentEvent) {
      return
    }

    val component = event.component
    val popup = ComponentUtil.getParentOfType(JPopupMenu::class.java, component)
    if (popup != null && popup.invoker === this.component && popup.isShowing()) {
      val bounds = popup.bounds
      if (bounds.isEmpty) {
        return
      }

      bounds.location = popup.locationOnScreen
      if (startMousePoint!!.x < bounds.x) {
        upperTargetPoint = Point(bounds.x, bounds.y)
        lowerTargetPoint = Point(bounds.x, bounds.y + bounds.height)
        closestHorizontalDistanceSoFar = abs(upperTargetPoint!!.x - xClosestToTargetSoFar)
      }
      if (startMousePoint!!.x > bounds.x + bounds.width) {
        upperTargetPoint = Point(bounds.x + bounds.width, bounds.y)
        lowerTargetPoint = Point(bounds.x + bounds.width, bounds.y + bounds.height)
        closestHorizontalDistanceSoFar = abs(upperTargetPoint!!.x - xClosestToTargetSoFar)
      }
    }
  }

  override fun dispatch(e: AWTEvent): Boolean {
    val callbackAlarm = callbackAlarm
    if (e !is MouseEvent || upperTargetPoint == null || lowerTargetPoint == null || callbackAlarm == null) {
      return false
    }

    if (e.getID() == MouseEvent.MOUSE_PRESSED || e.getID() == MouseEvent.MOUSE_RELEASED || e.getID() == MouseEvent.MOUSE_CLICKED) {
      return false
    }

    val point = e.locationOnScreen
    val bounds = component!!.bounds
    bounds.location = component!!.locationOnScreen
    val insideTarget = bounds.contains(point)
    val horizontalDistance = abs(upperTargetPoint!!.x - point.x)
    if (!insideTarget && horizontalDistance < closestHorizontalDistanceSoFar) {
      closestHorizontalDistanceSoFar = horizontalDistance
    }
    val startedToMoveAway = !insideTarget && horizontalDistance >= closestHorizontalDistanceSoFar + JBUI.scale(MOVING_AWAY_THRESHOLD)
    val isMouseMovingTowardsSubmenu = insideTarget || (
      !startedToMoveAway &&
      Polygon(intArrayOf(startMousePoint!!.x, upperTargetPoint!!.x, lowerTargetPoint!!.x),
              intArrayOf(startMousePoint!!.y, upperTargetPoint!!.y, lowerTargetPoint!!.y), 3)
        .contains(point)
    )
    eventToRedispatch = e
    if (!isMouseMovingTowardsSubmenu) {
      callbackAlarm.request()
    }
    else {
      callbackAlarm.cancel()
    }
    return true
  }

  override fun dispose() {
    component = null
    eventToRedispatch = null
    lowerTargetPoint = null
    upperTargetPoint = null
    startMousePoint = null
    Toolkit.getDefaultToolkit().removeAWTEventListener(this)
  }
}

private const val MOVING_AWAY_THRESHOLD = 16

private class SubElementSelector(private val owner: ActionMenu) {
  companion object {
    val isForceDisabled: Boolean = SystemInfo.isMacSystemMenu ||
                                   !Registry.`is`("ide.popup.menu.navigation.keyboard.selectFirstEnabledSubItem", false)
  }

  // A PATCH!!! Do not remove this code, otherwise you will lose all keyboard navigation in JMenuBar.
  @Suppress("GrazieInspection")
  @JvmField
  var stubItem: StubItem? = null

  @RequiresEdt
  fun ignoreNextSelectionRequest(timeoutMs: Int) {
    shouldIgnoreNextSelectionRequest = true
    shouldIgnoreNextSelectionRequestTimeoutMs = timeoutMs
    shouldIgnoreNextSelectionRequestSinceTimestamp = if (timeoutMs >= 0) {
      System.currentTimeMillis()
    }
    else {
      -1
    }
  }

  @RequiresEdt
  fun ignoreNextSelectionRequest() {
    ignoreNextSelectionRequest(-1)
  }

  @RequiresEdt
  fun cancelIgnoringOfNextSelectionRequest() {
    shouldIgnoreNextSelectionRequest = false
    shouldIgnoreNextSelectionRequestSinceTimestamp = -1
    shouldIgnoreNextSelectionRequestTimeoutMs = -1
  }

  @RequiresEdt
  fun selectSubElementIfNecessary() {
    val shouldIgnoreThisSelectionRequest = if (shouldIgnoreNextSelectionRequest) {
      if (shouldIgnoreNextSelectionRequestTimeoutMs >= 0) {
        System.currentTimeMillis() - shouldIgnoreNextSelectionRequestSinceTimestamp <= shouldIgnoreNextSelectionRequestTimeoutMs
      }
      else {
        true
      }
    }
    else {
      false
    }

    cancelIgnoringOfNextSelectionRequest()
    if (shouldIgnoreThisSelectionRequest) {
      return
    }

    val thisRequestId = ++currentRequestId
    SwingUtilities.invokeLater { selectFirstEnabledElement(thisRequestId) }
  }

  @RequiresEdt
  fun cancelNextSelection() {
    ++currentRequestId
  }

  private var shouldIgnoreNextSelectionRequest = false
  private var shouldIgnoreNextSelectionRequestSinceTimestamp = -1L
  private var shouldIgnoreNextSelectionRequestTimeoutMs = -1
  private var currentRequestId = -1

  init {
    if (isForceDisabled) {
      throw IllegalStateException("Attempt to create an instance of ActionMenu.SubElementSelector class when it is force disabled")
    }
  }

  @RequiresEdt
  private fun selectFirstEnabledElement(requestId: Int) {
    if (requestId != currentRequestId) {
      // the request was canceled or a newer request was created
      return
    }

    if (!owner.isSelected) {
      return
    }

    val menuSelectionManager = MenuSelectionManager.defaultManager()
    val currentSelectedPath = menuSelectionManager.getSelectedPath()
    if (currentSelectedPath.size < 2) {
      return
    }

    val lastElementInCurrentPath = currentSelectedPath[currentSelectedPath.size - 1]
    val newSelectionPath = when {
      lastElementInCurrentPath === stubItem -> currentSelectedPath.clone()
      lastElementInCurrentPath === owner.getPopupMenu() -> currentSelectedPath.copyOf(currentSelectedPath.size + 1)
      currentSelectedPath[currentSelectedPath.size - 2] === owner.getPopupMenu() &&
      !owner.getMenuComponents().contains(lastElementInCurrentPath!!.component) -> currentSelectedPath.clone()
      else -> return
    }

    val menuComponents = owner.getMenuComponents()
    for (component in menuComponents) {
      if (component != stubItem && component.isEnabled && component is JMenuItem) {
        newSelectionPath[newSelectionPath.size - 1] = component
        menuSelectionManager.setSelectedPath(newSelectionPath)
        return
      }
    }
  }
}
