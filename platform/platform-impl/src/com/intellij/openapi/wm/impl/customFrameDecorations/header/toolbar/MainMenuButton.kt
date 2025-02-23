// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.platform.ide.menu.IdeJMenuBar
import com.intellij.platform.ide.menu.createIdeMainMenuActionGroup
import com.intellij.ui.ComponentUtil
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.HierarchyEvent
import java.awt.event.KeyEvent
import javax.swing.*

private val LOG = logger<MainMenuButton>()

private const val MAIN_MENU_ACTION_ID = "MainMenuButton.ShowMenu"

@ApiStatus.Internal
class MainMenuButton(coroutineScope: CoroutineScope, icon: Icon = AllIcons.General.WindowsMenu_20x20, getItemToSelect: () -> Int) {

  internal var expandableMenu: ExpandableMenu? = null
    set(value) {
      field = value
      updateSubMenuShortcutsManager()
    }

  private val menuAction = ShowMenuAction(icon, getItemToSelect)
  private var disposable: Disposable? = null
  private var shortcutsChangeConnection: MessageBusConnection? = null
  private val subMenuShortcutsManager = SubMenuShortcutsManager()

  val button: ActionButton = createMenuButton(menuAction)

  var rootPane: JRootPane? = null
    set(value) {
      if (field !== value) {
        uninstall()
        field = value
        if (button.isShowing && value != null) {
          install()
        }
      }
    }

  init {
    button.addHierarchyListener { e ->
      // The root pane might have been replaced/removed (this happens when a frame is reused to open another project).
      rootPane = (ComponentUtil.getWindow(button) as? IdeFrameImpl?)?.rootPane
      if (e!!.changeFlags.toInt() and HierarchyEvent.SHOWING_CHANGED != 0) {
        if (button.isShowing) {
          install()
        }
        else {
          uninstall()
        }
      }
    }
    coroutineScope.launch(Dispatchers.EDT) {
      try {
        awaitCancellation()
      }
      finally {
        uninstall()
      }
    }
  }

  private fun initShortcutsChangeConnection() {
    shortcutsChangeConnection = ApplicationManager.getApplication().messageBus.connect()
      .apply {
        subscribe(KeymapManagerListener.TOPIC, object : KeymapManagerListener {
          override fun activeKeymapChanged(keymap: Keymap?) {
            button.update()
          }
        })
      }
  }

  private fun install() {
    val rootPaneCopy = rootPane
    if (rootPaneCopy == null) {
      thisLogger().warn("rootPane is not set, MainMenu button listeners are not installed")
      return
    }

    if (disposable == null) {
      disposable = Disposer.newDisposable()

      registerMenuButtonShortcut(rootPaneCopy)
      updateSubMenuShortcutsManager()
      initShortcutsChangeConnection()
    }
  }

  private fun uninstall() {
    disposable?.let { Disposer.dispose(it) }
    disposable = null
    shortcutsChangeConnection?.let { Disposer.dispose(it) }
    shortcutsChangeConnection = null
    subMenuShortcutsManager.reset()
  }

  private fun updateSubMenuShortcutsManager() {
    subMenuShortcutsManager.reset()

    val ideMenuBar = expandableMenu?.ideMenu
    if (button.isShowing && ideMenuBar != null) {
      subMenuShortcutsManager.init(ideMenuBar)
    }
  }

  private fun registerMenuButtonShortcut(component: JComponent) {
    val showMenuAction = ActionManager.getInstance().getAction(MAIN_MENU_ACTION_ID)
    if (showMenuAction == null) {
      LOG.warn("Cannot find action by id $MAIN_MENU_ACTION_ID")
      return
    }
    menuAction.registerCustomShortcutSet(showMenuAction.shortcutSet, component, disposable)
    button.update() // Update shortcut in tooltip
  }

  @ApiStatus.Internal
  inner class ShowMenuAction(icon: Icon, val getItemToSelect: () -> Int) : LightEditCompatible, DumbAwareAction(IdeBundle.messagePointer("main.toolbar.menu.button"), icon) {

    override fun actionPerformed(e: AnActionEvent) {
      if (expandableMenu?.isEnabled() == true) {
        expandableMenu!!.switchState(itemInd = getItemToSelect.invoke())
      } else {
        showPopup(e.dataContext)
      }
    }
  }

  fun showPopup(context: DataContext, actionToShow: AnAction? = null) {
    @Suppress("SSBasedInspection")
    val mainMenu = runBlocking { createIdeMainMenuActionGroup() } ?: return
    val popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(null, mainMenu, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
                              ActionPlaces.MAIN_MENU)
      .apply { isShowSubmenuOnHover = true }
      .apply { setMinimumSize(Dimension(JBUI.CurrentTheme.CustomFrameDecorations.menuPopupMinWidth(), 0)) }
      as ListPopupImpl
    popup.showUnderneathOf(button)

    if (actionToShow != null) {
      for (listStep in popup.listStep.values) {
        listStep as PopupFactoryImpl.ActionItem
        if (listStep.action === actionToShow) {
          SwingUtilities.invokeLater {
            // Wait popup showing
            popup.selectAndExpandValue(listStep)
          }
        }
      }
    }
  }

  private inner class ShowSubMenuAction(private val actionMenu: ActionMenu) : AbstractAction() {

    private val actionToShow = actionMenu.anAction
    private val keyStroke = KeyStroke.getKeyStroke(actionMenu.mnemonic, KeyEvent.ALT_DOWN_MASK)
    @NlsSafe
    private val actionMapKey = "MainMenuButton action ${actionToShow.templateText}"

    override fun actionPerformed(e: ActionEvent?) {
      if (!UISettings.getInstance().disableMnemonics) {
        if (expandableMenu?.isEnabled() == true) {
          expandableMenu!!.switchState(actionMenu)
        } else {
          val component = IdeFocusManager.getGlobalInstance().focusOwner ?: button
          showPopup(DataManager.getInstance().getDataContext(component), actionToShow)
        }
      }
    }

    fun register(shortcutsOwner: JComponent) {
      val inputMap = shortcutsOwner.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
      val actionMap = shortcutsOwner.actionMap
      inputMap.put(keyStroke, actionMapKey)
      actionMap.put(actionMapKey, this)
    }

    fun unregister(shortcutsOwner: JComponent) {
      val inputMap = shortcutsOwner.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
      val actionMap = shortcutsOwner.actionMap
      inputMap.remove(keyStroke)
      actionMap.remove(actionMapKey)
    }

    fun isSame(other: ShowSubMenuAction): Boolean {
      return actionToShow === other.actionToShow &&
             keyStroke.equals(other.keyStroke) &&
             actionMapKey == other.actionMapKey
    }
  }

  private inner class SubMenuShortcutsManager {

    private var ideMenuBar: IdeJMenuBar? = null
    private val listener = Runnable {
      ideMenuBar?.let {
        updateKeyStrokes(it.rootMenuItems)
      }
    }
    private var registeredActions = mutableListOf<ShowSubMenuAction>()

    fun init(ideMenuBar: IdeJMenuBar) {
      reset()
      this.ideMenuBar = ideMenuBar
      ideMenuBar.addUpdateGlobalMenuRootsListener(listener)
      updateKeyStrokes(ideMenuBar.rootMenuItems)
    }

    fun reset() {
      updateKeyStrokes(emptyList())
      ideMenuBar?.removeUpdateGlobalMenuRootsListener(listener)
      ideMenuBar = null
    }

    /**
     * Already registered actions are not re-registered, otherwise they will take higher priority over existing mnemonics in
     * other places of IDE
     */
    private fun updateKeyStrokes(actionMenus: List<ActionMenu>) {
      val existingActionMenus = mutableListOf<ShowSubMenuAction>()
      val newActionMenus = actionMenus.mapNotNull { if (it.mnemonic > 0) ShowSubMenuAction(it) else null }.toMutableList()

      for (action in registeredActions) {
        val index = newActionMenus.indexOfFirst { it.isSame(action) }
        if (index >= 0) {
          existingActionMenus.add(newActionMenus.removeAt(index))
        } else {
          action.unregister(button)
        }
      }
      registeredActions.clear()

      for (action in newActionMenus) {
        action.register(button)
      }

      registeredActions.addAll(existingActionMenus)
      registeredActions.addAll(newActionMenus)
    }
  }
}

private fun createMenuButton(action: AnAction): ActionButton {
  val button = object : ActionButton(action, PresentationFactory().getPresentation(action),
                                     ActionPlaces.MAIN_MENU, { ActionToolbar.experimentalToolbarMinimumButtonSize() }) {
    override fun getDataContext(): DataContext {
      return runCatching { DataManager.getInstance().dataContextFromFocusAsync.blockingGet(200) }.getOrNull() ?: super.getDataContext()
    }

    // Dynamically adjusts the insets of the component based on its height. This approach ensures alignment with the menu
    override fun getInsets(): Insets? {
      val ins = super.getInsets()
      val topBottomInset = this.height / scale(8)
      return JBUI.insets(topBottomInset,ins.left,  topBottomInset, ins.right)
    }
  }

  button.setLook(HeaderToolbarButtonLook(iconSize = { JBUI.CurrentTheme.Toolbar.burgerMenuButtonIconSize() }))
  return button
}
