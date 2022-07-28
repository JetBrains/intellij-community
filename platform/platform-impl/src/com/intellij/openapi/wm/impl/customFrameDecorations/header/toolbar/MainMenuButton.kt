// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.IconManager
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import javax.swing.JComponent

@ApiStatus.Internal
internal class MainMenuButton {

  private val menuAction = ShowMenuAction()

  val button: ActionButton = createMenuButton(menuAction)
  val menuShortcutHandler = MainMenuMnemonicHandler(menuAction)

  private inner class ShowMenuAction : DumbAwareAction() {

    private val icon = IconManager.getInstance().getIcon("expui/general/windowsMenu@20x20.svg", AllIcons::class.java)

    override fun update(e: AnActionEvent) {
      e.presentation.icon = icon
      e.presentation.text = IdeBundle.message("main.toolbar.menu.button")
    }

    override fun actionPerformed(e: AnActionEvent) = createPopup(e.dataContext).showUnderneathOf(button)

    private fun createPopup(context: DataContext): JBPopup {
      val mainMenu = ActionManager.getInstance().getAction(IdeActions.GROUP_MAIN_MENU) as ActionGroup
      return JBPopupFactory.getInstance()
        .createActionGroupPopup(null, mainMenu, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true,
                                ActionPlaces.MAIN_MENU_IN_POPUP)
        .apply { setShowSubmenuOnHover(true) }
        .apply { setMinimumSize(Dimension(JBUI.CurrentTheme.CustomFrameDecorations.menuPopupMinWidth(), 0)) }
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

class MainMenuMnemonicHandler(private val action: AnAction) : Disposable {
  private var disposable: CheckedDisposable? = null

  fun registerShortcuts(component: JComponent) {
    if (disposable?.isDisposed != false) {
      disposable = Disposer.newCheckedDisposable()
    }

    ActionManagerEx.withLazyActionManager(null) { actionManager ->
      if (disposable?.isDisposed != false) {
        return@withLazyActionManager
      }

      val showMenuAction = actionManager.getAction("MainMenuButton.ShowMenu")
      if (showMenuAction == null) {
        thisLogger().warn("Cannot find action by id ${"MainMenuButton.ShowMenu"}")
      }
      val shortcutSet = showMenuAction?.shortcutSet ?: CustomShortcutSet.EMPTY
      action.registerCustomShortcutSet(shortcutSet, component, disposable)
    }
  }

  fun unregisterShortcuts() {
    disposable?.let { Disposer.dispose(it) }
  }

  override fun dispose() = unregisterShortcuts()
}
