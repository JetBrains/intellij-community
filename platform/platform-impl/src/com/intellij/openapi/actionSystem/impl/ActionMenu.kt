// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.IdeEventQueue.Companion.getInstance
import com.intellij.ide.ui.UISettings.Companion.instanceOrNull
import com.intellij.internal.inspector.UiInspectorContextProvider
import com.intellij.internal.inspector.UiInspectorUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.MainMenuPresentationAware
import com.intellij.openapi.actionSystem.impl.actionholder.ActionRef
import com.intellij.openapi.actionSystem.impl.actionholder.createActionRef
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.util.*
import com.intellij.openapi.util.IconLoader.getDarkIcon
import com.intellij.openapi.util.IconLoader.getDisabledIcon
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.StatusBar
import com.intellij.ui.ColorUtil
import com.intellij.ui.ComponentUtil
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBMenu
import com.intellij.ui.icons.getMenuBarIcon
import com.intellij.ui.mac.foundation.NSDefaults
import com.intellij.ui.mac.screenmenu.Menu
import com.intellij.ui.plaf.beg.BegMenuItemUI
import com.intellij.ui.plaf.beg.IdeaMenuUI
import com.intellij.util.*
import com.intellij.util.concurrency.EdtScheduledExecutorService
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.*
import java.awt.event.AWTEventListener
import java.awt.event.ComponentEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.*
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import javax.swing.event.MenuEvent
import javax.swing.event.MenuListener

class ActionMenu @JvmOverloads constructor(private val myContext: DataContext?,
                                           place: String,
                                           group: ActionGroup,
                                           presentationFactory: PresentationFactory,
                                           enableMnemonics: Boolean,
                                           useDarkIcons: Boolean,
                                           headerMenuItem: Boolean = false) : JBMenu() {
  private val myPlace: String?
  private val myGroup: ActionRef<ActionGroup>
  private val myPresentationFactory: PresentationFactory
  private val myPresentation: Presentation
  private var myMnemonicEnabled: Boolean
  private var myStubItem: StubItem? = null // A PATCH!!! Do not remove this code, otherwise you will lose all keyboard navigation in JMenuBar.
  private val myUseDarkIcons: Boolean
  private var myDisposable: Disposable? = null
  var screenMenuPeer: Menu? = null
  private val mySubElementSelector: SubElementSelector?
  val isHeaderMenuItem: Boolean
  val anAction: AnAction
    get() = myGroup.getAction()

  override fun removeNotify() {
    super.removeNotify()
    if (myDisposable != null) {
      Disposer.dispose(myDisposable!!)
      myDisposable = null
    }
  }

  private var specialMenu: JPopupMenu? = null

  init {
    myPlace = place
    myGroup = createActionRef(group)
    myPresentationFactory = presentationFactory
    myPresentation = myPresentationFactory.getPresentation(group)
    myMnemonicEnabled = enableMnemonics
    myUseDarkIcons = useDarkIcons
    isHeaderMenuItem = headerMenuItem
    mySubElementSelector = if (SubElementSelector.isForceDisabled) null else SubElementSelector(this)
    if (Menu.isJbScreenMenuEnabled() && ActionPlaces.MAIN_MENU == myPlace) {
      val screenMenuPeer = Menu(myPresentation.getText(enableMnemonics))
      this.screenMenuPeer = screenMenuPeer
      screenMenuPeer.setOnOpen(Runnable {
        // NOTE: setSelected(true) calls fillMenu internally
        setSelected(true)
      }, this)
      screenMenuPeer.setOnClose(Runnable { setSelected(false) }, this)
      screenMenuPeer.listenPresentationChanges(myPresentation)
    }
    else {
      screenMenuPeer = null
      updateUI()
    }
    init()
    if (screenMenuPeer == null) {
      // also triggering initialization of private field "popupMenu" from JMenu with our own JBPopupMenu
      BegMenuItemUI.registerMultiChoiceSupport(getPopupMenu()) { popupMenu: JPopupMenu? ->
        Utils.updateMenuItems(
          popupMenu!!, dataContext, myPlace, myPresentationFactory)
      }
    }
  }

  companion object {
    /**
     * By default, a "performable" non-empty popup action group menu item still shows a submenu.
     * Use this key to disable the submenu and avoid children expansion on update as follows:
     *
     *
     * `presentation.putClientProperty(ActionMenu.SUPPRESS_SUBMENU, true)`.
     *
     *
     * Both ordinary and template presentations are supported.
     *
     * @see Presentation.setPerformGroup
     */
    @JvmField
    val SUPPRESS_SUBMENU: Key<Boolean> = Key.create("SUPPRESS_SUBMENU")

    @JvmStatic
    fun shouldConvertIconToDarkVariant(): Boolean {
      return JBColor.isBright() && ColorUtil.isDark(JBColor.namedColor("MenuItem.background", 0xffffff))
    }

    @JvmStatic
    val isShowNoIcons: Boolean
      get() {
        return SystemInfoRt.isMac && (Registry.get("ide.macos.main.menu.alignment.options").isOptionEnabled("No icons") || ExperimentalUI.isNewUI())
      }

    @JvmStatic
    fun isShowNoIcons(action: AnAction?): Boolean {
      if (action == null) {
        return false
      }
      return if (action is MainMenuPresentationAware && (action as MainMenuPresentationAware).alwaysShowIconInMainMenu()) {
        false
      }
      else isShowNoIcons
    }

    @JvmStatic
    val isAligned: Boolean
      get() = SystemInfoRt.isMac && Registry.get("ide.macos.main.menu.alignment.options").isOptionEnabled("Aligned")
    @JvmStatic
    val isAlignedInGroup: Boolean
      get() = SystemInfoRt.isMac && Registry.get("ide.macos.main.menu.alignment.options").isOptionEnabled("Aligned in group")

    @JvmStatic
    fun showDescriptionInStatusBar(isIncluded: Boolean, component: Component?, description: @NlsContexts.StatusBarText String?) {
      val frame = (if (component is IdeFrame) component else SwingUtilities.getAncestorOfClass(IdeFrame::class.java, component)) as? IdeFrame
      var statusBar: StatusBar? = null
      if (frame != null && frame.getStatusBar().also { statusBar = it!! } != null) {
        statusBar!!.setInfo(if (isIncluded) description else null)
      }
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
        UiInspectorUtil.collectActionGroupInfo("Menu", myGroup.getAction(), myPlace)
      })
    }
    return super.getPopupMenu()
  }

  override fun updateUI() {
    // null myPlace means that Swing calls updateUI before our constructor
    if (screenMenuPeer != null || myPlace == null) {
      return
    }
    setUI(IdeaMenuUI.createUI(this))
    setFont(FontUtil.getMenuFont())
    val popupMenu = getPopupMenu()
    if (popupMenu != null) {
      popupMenu.updateUI()
    }
  }

  private fun init() {
    val macSystemMenu = SystemInfo.isMacSystemMenu && isMainMenuPlace
    myStubItem = if (macSystemMenu) null else StubItem()
    addStubItem()
    setBorderPainted(false)
    val menuListener = MenuListenerImpl()
    addMenuListener(menuListener)
    getModel().addChangeListener(menuListener)
    updateFromPresentation(myMnemonicEnabled)
  }

  val isMainMenuPlace: Boolean
    get() = myPlace == ActionPlaces.MAIN_MENU

  fun updateFromPresentation(enableMnemonics: Boolean) {
    myMnemonicEnabled = enableMnemonics
    isVisible = myPresentation.isVisible
    setEnabled(myPresentation.isEnabled)
    setText(myPresentation.getText(myMnemonicEnabled))
    mnemonic = myPresentation.getMnemonic()
    displayedMnemonicIndex = myPresentation.getDisplayedMnemonicIndex()
    updateIcon()
  }

  private fun addStubItem() {
    if (myStubItem != null) {
      add(myStubItem)
    }
  }

  @Throws(IllegalArgumentException::class)
  override fun setDisplayedMnemonicIndex(index: Int) {
    super.setDisplayedMnemonicIndex(if (myMnemonicEnabled) index else -1)
  }

  override fun setMnemonic(mnemonic: Int) {
    super.setMnemonic(if (myMnemonicEnabled) mnemonic else 0)
  }

  private fun updateIcon() {
    val settings = instanceOrNull
    var icon = myPresentation.icon
    if (icon != null && settings != null && settings.showIconsInMenus) {
      if (SystemInfo.isMacSystemMenu && ActionPlaces.MAIN_MENU == myPlace) {
        // JDK can't paint correctly our HiDPI icons at the system menu bar
        icon = getMenuBarIcon(icon, myUseDarkIcons)
      }
      else if (shouldConvertIconToDarkVariant()) {
        icon = getDarkIcon(icon, true)
      }
      if (isShowNoIcons) {
        setIcon(null)
        setDisabledIcon(null)
      }
      else {
        setIcon(icon)
        val presentationDisabledIcon = myPresentation.disabledIcon
        setDisabledIcon(presentationDisabledIcon ?: getDisabledIcon(icon))
        screenMenuPeer?.setIcon(icon)
      }
    }
  }

  override fun menuSelectionChanged(isIncluded: Boolean) {
    super.menuSelectionChanged(isIncluded)
    showDescriptionInStatusBar(isIncluded, this, myPresentation.description)
  }

  override fun processMouseEvent(e: MouseEvent) {
    var shouldCancelIgnoringOfNextSelectionRequest = false
    if (mySubElementSelector != null) {
      when (e.id) {
        MouseEvent.MOUSE_PRESSED -> {
          mySubElementSelector.ignoreNextSelectionRequest()
          shouldCancelIgnoringOfNextSelectionRequest = true
        }
        MouseEvent.MOUSE_ENTERED -> mySubElementSelector.ignoreNextSelectionRequest(delay * 2)
      }
    }
    try {
      super.processMouseEvent(e)
    }
    finally {
      if (shouldCancelIgnoringOfNextSelectionRequest) {
        mySubElementSelector!!.cancelIgnoringOfNextSelectionRequest()
      }
    }
  }

  private inner class MenuListenerImpl : ChangeListener, MenuListener {
    var myDelayedClear: ScheduledFuture<*>? = null
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
      if (myDisposable != null) {
        Disposer.dispose(myDisposable!!)
        myDisposable = null
      }
      onMenuHidden()
      mySubElementSelector?.cancelNextSelection()
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
        myDelayedClear = EdtScheduledExecutorService.getInstance().schedule(clearSelf, 1000, TimeUnit.MILLISECONDS)
      }
      else {
        clearSelf.run()
      }
    }

    private fun menuSelected() {
      val helper = UsabilityHelper(this@ActionMenu)
      if (myDisposable == null) {
        myDisposable = Disposer.newDisposable()
      }
      Disposer.register(myDisposable!!, helper)
      if (myDelayedClear != null) {
        myDelayedClear!!.cancel(false)
        myDelayedClear = null
        clearItems()
      }
      if (SystemInfo.isMacSystemMenu && ActionPlaces.MAIN_MENU == myPlace) {
        fillMenu()
      }
    }
  }

  override fun setPopupMenuVisible(b: Boolean) {
    if (b && !(SystemInfo.isMacSystemMenu && ActionPlaces.MAIN_MENU == myPlace)) {
      fillMenu()
      if (!isSelected) {
        return
      }
    }
    super.setPopupMenuVisible(b)
    if (b && mySubElementSelector != null) {
      mySubElementSelector.selectSubElementIfNecessary()
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
          if (!SystemInfo.isMac) {
            menuComponent.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F24, 0))
          }
        }
      }
    }
    removeAll()
    validate()
  }

  private val dataContext: DataContext
    private get() {
      var context: DataContext
      if (myContext != null) {
        context = myContext
      }
      else {
        val dataManager = DataManager.getInstance()
        @Suppress("deprecation") val contextFromFocus = dataManager.getDataContext()
        context = contextFromFocus
        if (PlatformCoreDataKeys.CONTEXT_COMPONENT.getData(context) == null) {
          val frame = ComponentUtil.getParentOfType(IdeFrame::class.java as Class<out IdeFrame?>, this as Component)
          context = dataManager.getDataContext(IdeFocusManager.getGlobalInstance().getLastFocusedFor(frame as Window?))
        }
        context = Utils.wrapDataContext(context)
      }
      return context
    }

  fun fillMenu() {
    val context = dataContext
    val isDarkMenu = SystemInfo.isMacSystemMenu && NSDefaults.isDarkMenuBar()
    Utils.fillMenu(myGroup.getAction(), this, myMnemonicEnabled, myPresentationFactory, context, myPlace!!, true, isDarkMenu,
                   RelativePoint.getNorthEastOf(this)) { !isSelected }
  }

  private class UsabilityHelper(component: Component) : IdeEventQueue.EventDispatcher, AWTEventListener, Disposable {
    private var myComponent: Component?
    private var myStartMousePoint: Point?
    private var myUpperTargetPoint: Point? = null
    private var myLowerTargetPoint: Point? = null
    private var callbackAlarm: SingleAlarm?
    private var myEventToRedispatch: MouseEvent? = null

    init {
      callbackAlarm = SingleAlarm({
                                      //Disposer.dispose(callbackAlarm!!)
                                      callbackAlarm = null
                                      if (myEventToRedispatch != null) {
                                        getInstance().dispatchEvent(myEventToRedispatch!!)
                                      }
                                    }, 50, this, Alarm.ThreadToUse.SWING_THREAD, ModalityState.any())
      myComponent = component
      val info = MouseInfo.getPointerInfo()
      myStartMousePoint = info?.location
      if (myStartMousePoint != null) {
        Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.COMPONENT_EVENT_MASK)
        getInstance().addDispatcher(this, this)
      }
    }

    override fun eventDispatched(event: AWTEvent) {
      if (event is ComponentEvent) {
        val component: Component = event.component
        val popup = ComponentUtil.getParentOfType(JPopupMenu::class.java as Class<out JPopupMenu?>, component)
        if (popup != null && popup.invoker === myComponent && popup.isShowing()) {
          val bounds = popup.bounds
          if (bounds.isEmpty) return
          bounds.location = popup.locationOnScreen
          if (myStartMousePoint!!.x < bounds.x) {
            myUpperTargetPoint = Point(bounds.x, bounds.y)
            myLowerTargetPoint = Point(bounds.x, bounds.y + bounds.height)
          }
          if (myStartMousePoint!!.x > bounds.x + bounds.width) {
            myUpperTargetPoint = Point(bounds.x + bounds.width, bounds.y)
            myLowerTargetPoint = Point(bounds.x + bounds.width, bounds.y + bounds.height)
          }
        }
      }
    }

    override fun dispatch(e: AWTEvent): Boolean {
      if (e is MouseEvent && myUpperTargetPoint != null && myLowerTargetPoint != null && callbackAlarm != null) {
        if (e.getID() == MouseEvent.MOUSE_PRESSED || e.getID() == MouseEvent.MOUSE_RELEASED || e.getID() == MouseEvent.MOUSE_CLICKED) {
          return false
        }
        val point = e.locationOnScreen
        val bounds = myComponent!!.bounds
        bounds.location = myComponent!!.locationOnScreen
        val isMouseMovingTowardsSubmenu = bounds.contains(point) || Polygon(intArrayOf(
          myStartMousePoint!!.x, myUpperTargetPoint!!.x, myLowerTargetPoint!!.x), intArrayOf(myStartMousePoint!!.y, myUpperTargetPoint!!.y,
                                                                                             myLowerTargetPoint!!.y),
                                                                            3).contains(point)
        myEventToRedispatch = e
        if (!isMouseMovingTowardsSubmenu) {
          callbackAlarm!!.request()
        }
        else {
          callbackAlarm!!.cancel()
        }
        return true
      }
      return false
    }

    override fun dispose() {
      myComponent = null
      myEventToRedispatch = null
      myLowerTargetPoint = null
      myUpperTargetPoint = myLowerTargetPoint
      myStartMousePoint = myUpperTargetPoint
      Toolkit.getDefaultToolkit().removeAWTEventListener(this)
    }
  }

  private class SubElementSelector(owner: ActionMenu) {
    @RequiresEdt
    fun ignoreNextSelectionRequest(timeoutMs: Int) {
      myShouldIgnoreNextSelectionRequest = true
      myShouldIgnoreNextSelectionRequestTimeoutMs = timeoutMs
      myShouldIgnoreNextSelectionRequestSinceTimestamp = if (timeoutMs >= 0) {
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
      myShouldIgnoreNextSelectionRequest = false
      myShouldIgnoreNextSelectionRequestSinceTimestamp = -1
      myShouldIgnoreNextSelectionRequestTimeoutMs = -1
    }

    @RequiresEdt
    fun selectSubElementIfNecessary() {
      val shouldIgnoreThisSelectionRequest: Boolean
      shouldIgnoreThisSelectionRequest = if (myShouldIgnoreNextSelectionRequest) {
        if (myShouldIgnoreNextSelectionRequestTimeoutMs >= 0) {
          System.currentTimeMillis() - myShouldIgnoreNextSelectionRequestSinceTimestamp <= myShouldIgnoreNextSelectionRequestTimeoutMs
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
      val thisRequestId = ++myCurrentRequestId
      SwingUtilities.invokeLater { selectFirstEnabledElement(thisRequestId) }
    }

    @RequiresEdt
    fun cancelNextSelection() {
      ++myCurrentRequestId
    }

    private val myOwner: ActionMenu
    private var myShouldIgnoreNextSelectionRequest: Boolean
    private var myShouldIgnoreNextSelectionRequestSinceTimestamp: Long
    private var myShouldIgnoreNextSelectionRequestTimeoutMs: Int
    private var myCurrentRequestId: Int

    init {
      if (isForceDisabled) {
        throw IllegalStateException("Attempt to create an instance of ActionMenu.SubElementSelector class when it is force disabled")
      }
      myOwner = owner
      myShouldIgnoreNextSelectionRequest = false
      myShouldIgnoreNextSelectionRequestSinceTimestamp = -1
      myShouldIgnoreNextSelectionRequestTimeoutMs = -1
      myCurrentRequestId = -1
    }

    @RequiresEdt
    private fun selectFirstEnabledElement(requestId: Int) {
      if (requestId != myCurrentRequestId) {
        // the request was cancelled or a newer request was created
        return
      }
      if (!myOwner.isSelected) {
        return
      }
      val menuSelectionManager = MenuSelectionManager.defaultManager()
      val currentSelectedPath = menuSelectionManager.getSelectedPath()
      if (currentSelectedPath.size < 2) {
        return
      }
      val lastElementInCurrentPath = currentSelectedPath[currentSelectedPath.size - 1]
      val newSelectionPath: Array<MenuElement?>
      newSelectionPath = if (lastElementInCurrentPath === myOwner.myStubItem) {
        currentSelectedPath.clone()
      }
      else if (lastElementInCurrentPath === myOwner.getPopupMenu()) {
        currentSelectedPath.copyOf(currentSelectedPath.size + 1)
      }
      else if (currentSelectedPath[currentSelectedPath.size - 2] === myOwner.getPopupMenu() &&
               !ArrayUtil.contains(lastElementInCurrentPath!!.component, *myOwner.getMenuComponents())) {
        currentSelectedPath.clone()
      }
      else {
        return
      }
      val menuComponents = myOwner.getMenuComponents()
      for (component in menuComponents) {
        if (component !== myOwner.myStubItem && component.isEnabled && component is JMenuItem) {
          newSelectionPath[newSelectionPath.size - 1] = component as MenuElement
          menuSelectionManager.setSelectedPath(newSelectionPath)
          return
        }
      }
    }

    companion object {
      val isForceDisabled: Boolean = SystemInfo.isMacSystemMenu ||
                                     !Registry.`is`("ide.popup.menu.navigation.keyboard.selectFirstEnabledSubItem", false)
    }
  }
}