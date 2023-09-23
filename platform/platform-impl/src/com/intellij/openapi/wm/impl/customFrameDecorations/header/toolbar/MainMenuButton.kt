// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.icons.ExpUiIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.ide.menu.IdeJMenuBar
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.HierarchyEvent
import java.awt.event.KeyEvent
import javax.swing.*

@ApiStatus.Internal
class MainMenuButton {

  internal var expandableMenu: ExpandableMenu? = null
    set(value) {
      field = value
      updateSubMenuShortcutsManager()
    }

  private val menuAction = ShowMenuAction()
  private var disposable: Disposable? = null
  private var shortcutsChangeConnection: MessageBusConnection? = null
  private val subMenuShortcutsManager = SubMenuShortcutsManager()

  val button: ActionButton = createMenuButton(menuAction)

  var rootPane: JRootPane? = null
    set(value) {
      if (field !== value) {
        uninstall()
        field = value
        if (button.isShowing) {
          install()
        }
      }
    }

  init {
    button.addHierarchyListener { e ->
      if (e!!.changeFlags.toInt() and HierarchyEvent.SHOWING_CHANGED != 0) {
        if (button.isShowing) {
          install()
        }
        else {
          uninstall()
        }
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
  inner class ShowMenuAction : LightEditCompatible, DumbAwareAction(
    IdeBundle.messagePointer("main.toolbar.menu.button"),
    ExpUiIcons.General.WindowsMenu_20x20) {

    override fun actionPerformed(e: AnActionEvent) {
      if (expandableMenu?.isEnabled() == true) {
        expandableMenu!!.switchState()
      } else {
        showPopup(e.dataContext)
      }
    }
  }

  fun showPopup(context: DataContext, actionToShow: AnAction? = null) {
    val mainMenu = getMainMenuGroup()
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
        if (listStep.action.unwrap() === actionToShow.unwrap()) {
          SwingUtilities.invokeLater {
            // Wait popup showing
            popup.selectAndExpandValue(listStep)
          }
        }
      }
    }
  }

  private inner class ShowSubMenuAction(actionMenu: ActionMenu) : AbstractAction() {

    private val actionToShow = actionMenu.anAction
    private val keyStroke = KeyStroke.getKeyStroke(actionMenu.mnemonic, KeyEvent.ALT_DOWN_MASK)
    @NlsSafe
    private val actionMapKey = "MainMenuButton action ${actionToShow.templateText}"

    override fun actionPerformed(e: ActionEvent?) {
      if (!UISettings.getInstance().disableMnemonics) {
        if (expandableMenu?.isEnabled() == true) {
          expandableMenu!!.switchState(actionToShow)
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
  }

  button.setLook(HeaderToolbarButtonLook(iconSize = { JBUI.CurrentTheme.Toolbar.burgerMenuButtonIconSize() }))
  return button
}

internal fun getMainMenuGroup(): ActionGroup {
  val mainMenuGroup = CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_MAIN_MENU)
  mainMenuGroup as ActionGroup
  return DefaultActionGroup(
    mainMenuGroup.getChildren(null).mapNotNull { child ->
      if (child is ActionGroup) {
        // Wrap action groups to force them to be popup groups,
        // otherwise they end up as separate items in the burger menu (IDEA-294669).
        ActionGroupPopupWrapper(child)
      }
      else {
        LOG.error("A top-level child of the main menu is not an action group: $child")
        null
      }
    }
  )
}

private class ActionGroupPopupWrapper(val wrapped: ActionGroup) : ActionGroupWrapper(wrapped) {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isPopupGroup = true
  }
}

private fun AnAction.unwrap(): AnAction =
  if (this is ActionGroupPopupWrapper)
    this.wrapped
  else
    this

private const val MAIN_MENU_ACTION_ID = "MainMenuButton.ShowMenu"

private val LOG = logger<MainMenuButton>()
