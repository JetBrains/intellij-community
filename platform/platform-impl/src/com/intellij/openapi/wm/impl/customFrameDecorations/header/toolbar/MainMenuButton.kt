// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UISettings.Companion.getInstance
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.ui.IconManager
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.util.PopupImplUtil
import com.intellij.util.messages.MessageBusConnection
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.HierarchyEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

@ApiStatus.Internal
internal class MainMenuButton {

  private val menuAction = ShowMenuAction()
  private var disposable: Disposable? = null
  private var shortcutsChangeConnection: MessageBusConnection? = null
  private var registeredKeyStrokes = mutableListOf<KeyStroke>()

  val button: ActionButton = createMenuButton(menuAction)
  var rootPane: JComponent? = null
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
      registerSubMenuShortcuts(rootPaneCopy)
      initShortcutsChangeConnection()
    }
  }

  private fun uninstall() {
    disposable?.let { Disposer.dispose(it) }
    disposable = null
    shortcutsChangeConnection?.let { Disposer.dispose(it) }
    shortcutsChangeConnection = null
    rootPane?.let {
      for (keyStroke in registeredKeyStrokes) {
        it.unregisterKeyboardAction(keyStroke)
      }
    }
    registeredKeyStrokes.clear()
  }

  private fun registerMenuButtonShortcut(component: JComponent) {
    val showMenuAction = ActionManager.getInstance().getAction(MAIN_MENU_ACTION_ID)
    if (showMenuAction == null) {
      logger<MainMenuButton>().warn("Cannot find action by id $MAIN_MENU_ACTION_ID")
      return
    }
    menuAction.registerCustomShortcutSet(showMenuAction.shortcutSet, component, disposable)
    button.update() // Update shortcut in tooltip
  }

  private fun registerSubMenuShortcuts(component: JComponent) {
    val mainMenu = getMainMenuGroup()
    for (action in mainMenu.getChildren(null)) {
      val mnemonic = action.templatePresentation.mnemonic
      if (mnemonic > 0) {
        val keyStroke = KeyStroke.getKeyStroke(mnemonic, KeyEvent.ALT_DOWN_MASK)
        component.registerKeyboardAction(ShowSubMenuAction(action), keyStroke, JComponent.WHEN_IN_FOCUSED_WINDOW)
        registeredKeyStrokes.add(keyStroke)
      }
    }
  }

  private inner class ShowMenuAction : DumbAwareAction() {

    private val icon = IconManager.getInstance().getIcon("expui/general/windowsMenu@20x20.svg", AllIcons::class.java)

    override fun update(e: AnActionEvent) {
      e.presentation.icon = icon
      e.presentation.text = IdeBundle.message("main.toolbar.menu.button")
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.BGT
    }

    override fun actionPerformed(e: AnActionEvent) = showPopup(e.dataContext)

    fun showPopup(context: DataContext, actionToShow: AnAction? = null) {
      val mainMenu = getMainMenuGroup()
      val popup = JBPopupFactory.getInstance()
        .createActionGroupPopup(null, mainMenu, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
                                ActionPlaces.MAIN_MENU)
        .apply { setShowSubmenuOnHover(true) }
        .apply { setMinimumSize(Dimension(JBUI.CurrentTheme.CustomFrameDecorations.menuPopupMinWidth(), 0)) }
        as ListPopupImpl
      PopupImplUtil.setPopupToggleButton(popup, button)
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
  }

  private inner class ShowSubMenuAction(private val actionToShow: AnAction) : ActionListener {

    override fun actionPerformed(e: ActionEvent?) {
      if (!getInstance().disableMnemonics) {
        menuAction.showPopup(DataManager.getInstance().getDataContext(button), actionToShow)
      }
    }
  }
}


private fun createMenuButton(action: AnAction): ActionButton {
  val button = object : ActionButton(action, PresentationFactory().getPresentation(action),
                                     ActionPlaces.MAIN_MENU, ActionToolbar.experimentalToolbarMinimumButtonSize()) {
    override fun getDataContext(): DataContext {
      return DataManager.getInstance().dataContextFromFocusAsync.blockingGet(200) ?: super.getDataContext()
    }
  }

  button.setLook(HeaderToolbarButtonLook())
  return button
}

private fun getMainMenuGroup(): ActionGroup {
  val mainMenuGroup = CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_MAIN_MENU)
  mainMenuGroup as ActionGroup
  return DefaultActionGroup(
    mainMenuGroup.getChildren(null).mapNotNull { child ->
      if (child is ActionGroup) {
        // Wrap action groups to force them to be popup groups,
        // otherwise they end up as separate items in the burger menu (IDEA-294669).
        ActionGroupPopupWrapper(child)
      } else {
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
