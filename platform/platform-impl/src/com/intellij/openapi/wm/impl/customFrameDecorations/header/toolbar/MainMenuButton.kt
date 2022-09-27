// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.IconManager
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.event.HierarchyEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

@ApiStatus.Internal
internal class MainMenuButton {

  private val menuAction = ShowMenuAction()
  private var disposable: CheckedDisposable? = null

  val button: ActionButton = createMenuButton(menuAction)
  var rootPane: JComponent? = null
    set(value) {
      if (field !== value) {
        unregisterShortcuts()
        field = value
        if (button.isShowing) {
          registerShortcuts()
        }
      }
    }

  init {
    button.addHierarchyListener { e ->
      if (e!!.changeFlags.toInt() and HierarchyEvent.SHOWING_CHANGED != 0) {
        if (button.isShowing) {
          registerShortcuts()
        }
        else {
          unregisterShortcuts()
        }
      }
    }
  }

  private fun registerShortcuts() {
    val rootPaneCopy = rootPane
    if (rootPaneCopy == null) {
      thisLogger().warn("rootPane is not set, MainMenu button listeners are not installed")
      return
    }

    if (disposable?.isDisposed != false) {
      disposable = Disposer.newCheckedDisposable()

      registerMenuButtonShortcut(rootPaneCopy)
      registerSubMenuShortcuts(rootPaneCopy)
    }
  }

  private fun unregisterShortcuts() {
    disposable?.let { Disposer.dispose(it) }
    disposable = null
  }

  private fun registerMenuButtonShortcut(component: JComponent) {
    ActionManagerEx.withLazyActionManager(null) { actionManager ->
      if (disposable?.isDisposed != false) {
        return@withLazyActionManager
      }

      val showMenuAction = actionManager.getAction("MainMenuButton.ShowMenu")
      if (showMenuAction == null) {
        thisLogger().warn("Cannot find action by id ${"MainMenuButton.ShowMenu"}")
      }
      val shortcutSet = showMenuAction?.shortcutSet ?: CustomShortcutSet.EMPTY
      menuAction.registerCustomShortcutSet(shortcutSet, component, disposable)
    }
  }

  private fun registerSubMenuShortcuts(component: JComponent) {
    val mainMenu = ActionManager.getInstance().getAction(IdeActions.GROUP_MAIN_MENU) as ActionGroup
    for (action in mainMenu.getChildren(null)) {
      val mnemonic = action.templatePresentation.mnemonic
      if (mnemonic > 0) {
        ShowSubMenu(action).registerCustomShortcutSet(CustomShortcutSet(KeyStroke.getKeyStroke(mnemonic, KeyEvent.ALT_DOWN_MASK)),
                                                      component, disposable)
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
      val mainMenu = ActionManager.getInstance().getAction(IdeActions.GROUP_MAIN_MENU) as ActionGroup
      val popup = JBPopupFactory.getInstance()
        .createActionGroupPopup(null, mainMenu, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
                                ActionPlaces.MAIN_MENU_IN_POPUP)
        .apply { setShowSubmenuOnHover(true) }
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
  }

  private inner class ShowSubMenu(private val actionToShow: AnAction) : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
      menuAction.showPopup(e.dataContext, actionToShow)
    }
  }
}


private fun createMenuButton(action: AnAction): ActionButton {
  val button = object : ActionButton(action, PresentationFactory().getPresentation(action),
                                     ActionPlaces.MAIN_MENU_IN_POPUP, Dimension(40, 40)) {
    override fun getDataContext(): DataContext {
      return DataManager.getInstance().dataContextFromFocusAsync.blockingGet(200) ?: super.getDataContext()
    }
  }
  button.setLook(HeaderToolbarButtonLook())
  return button
}
