// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.menu

import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.ActionPresentationDecorator
import com.intellij.openapi.actionSystem.impl.EMPTY_MENU_ACTION_ICON
import com.intellij.openapi.actionSystem.impl.PoppedIcon
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.actionSystem.impl.actionholder.createActionRef
import com.intellij.openapi.actionSystem.impl.isEnterKeyStroke
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.TransactionGuardImpl
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.getShortcutSetForDisplay
import com.intellij.openapi.keymap.impl.IdeKeyEventDispatcher
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.awaitFocusSettlesDown
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.ui.ComponentUtil
import com.intellij.ui.icons.getMenuBarIcon
import com.intellij.ui.mac.screenmenu.MenuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.EventQueue
import java.awt.event.InputEvent
import javax.swing.Icon
import javax.swing.JFrame
import javax.swing.KeyStroke

private val LOG = logger<MacNativeActionMenuItem>()

internal class MacNativeActionMenuItem(
  action: AnAction,
  private val place: String,
  private val context: DataContext,
  private val frame: JFrame,
  private val isMnemonicEnabled: Boolean,
  private val insideCheckedGroup: Boolean,
  private val useDarkIcons: Boolean,
) {
  private val actionRef = createActionRef(action)
  // do not expose presentation
  private val presentation = Presentation.newTemplatePresentation()

  private val isToggleable = action is Toggleable
  private var isToggled = action is Toggleable

  @JvmField
  internal val menuItemPeer = MenuItem().apply {
    setActionDelegate {
      // called on AppKit when user activates menu item
      if (isToggleable) {
        isToggled = !isToggled
        setState(isToggled)
      }
      EventQueue.invokeLater {
        if (presentation.isEnabledInModalContext || !isModalContextForMainMenu(context)) {
          performAction(actionRef.getAction(), place, presentation.clone(), context)
        }
        else if (ModalityState.current() == ModalityState.nonModal() &&
                 PopupUtil.getPopupContainerFor(context.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)) != null &&
                 IdeEventQueue.getInstance().popupManager.closeAllPopups(false)) {
          performAfterPopupsClose(actionRef.getAction(), place, frame)
        }
      }
    }
  }

  fun updateFromPresentation(presentation: Presentation) {
    this.presentation.copyFrom(presentation, null, true)
    val action = actionRef.getAction()
    isToggled = isToggleable && Toggleable.isSelected(presentation)

    updateIcon(presentation, action, useDarkIcons, isToggleable, isToggled, insideCheckedGroup)

    val shortcuts = getShortcutSetForDisplay(action).getShortcuts()
    var accelerator: KeyStroke? = null
    for (shortcut in shortcuts) {
      if (shortcut !is KeyboardShortcut) {
        continue
      }

      val firstKeyStroke = shortcut.firstKeyStroke
      // If the action has `Enter` shortcut, do not add it. Otherwise, user won't be able to choose any ActionMenuItem other than that
      if (!isEnterKeyStroke(firstKeyStroke)) {
        accelerator = firstKeyStroke
        if (KeymapUtil.isSimplifiedMacShortcuts) {
          menuItemPeer.setAcceleratorText(KeymapUtil.getPreferredShortcutText(shortcuts))
        }
      }
      break
    }

    val text = ActionPresentationDecorator.decorateTextIfNeeded(action, presentation.getText(isMnemonicEnabled))
    menuItemPeer.setLabel(text, accelerator)
    menuItemPeer.setEnabled(presentation.isEnabled)
  }

  private fun updateIcon(presentation: Presentation,
                         action: AnAction,
                         useDarkIcons: Boolean,
                         isToggleable: Boolean,
                         isToggled: Boolean,
                         insideCheckedGroup: Boolean) {
    if (isToggleable && (insideCheckedGroup || !UISettings.getInstance().showIconsInMenus) || presentation.icon == null) {
      menuItemPeer.setState(isToggled)
      menuItemPeer.setIcon(adjustIcon(presentation.icon, useDarkIcons, action, presentation))
    }
    else if (UISettings.getInstance().showIconsInMenus) {
      var icon = presentation.icon
      if (isToggleable && isToggled) {
        icon = icon?.let { PoppedIcon(it, 16, 16) }
      }
      menuItemPeer.setIcon(adjustIcon(
        if (presentation.isEnabled) icon else presentation.disabledIcon ?: icon?.let { IconLoader.getDisabledIcon(it) },
        useDarkIcons, action, presentation))
    }
  }

  private fun adjustIcon(icon: Icon?, useDarkIcons: Boolean, action: AnAction, presentation: Presentation): Icon? {
    return when {
      ActionMenu.isShowNoIcons(action, presentation) -> null
      !ActionMenu.isAligned || !ActionMenu.isAlignedInGroup -> icon?.let { getMenuBarIcon(it, useDarkIcons) }
      else -> icon ?: getMenuBarIcon(EMPTY_MENU_ACTION_ICON, useDarkIcons)
    }
  }
}

private fun performAction(action: AnAction, place: String, presentation: Presentation, context: DataContext) {
  (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity {
    //todo fix all clients and remove global lock here
    WriteIntentReadAction.run {
      val id = ActionManager.getInstance().getId(action)
      if (id != null) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed("context.menu.click.stats.${id.replace(' ', '.')}")
      }
      IdeFocusManager.findInstanceByContext(context).runOnOwnContext(context, Runnable {
        val currentEvent = IdeEventQueue.getInstance().trueCurrentEvent
        val event = AnActionEvent.createEvent(
          context, presentation, place, ActionUiKind.MAIN_MENU, currentEvent as? InputEvent)
        ActionUtil.performAction(action, event)
      })
    }
  }
}

/**
 * Performs the [action] after the popups close.
 * The function waits for the focus, takes a new data context of the [frame] and updates the [action] in it.
 * It does nothing when the new data context is a modal context.
 */
private fun performAfterPopupsClose(action: AnAction, place: String, frame: JFrame) {
  service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.EDT) {
    IdeFocusManager.getGlobalInstance().awaitFocusSettlesDown()
    val context = Utils.createAsyncDataContext(getDataContext(frame))
    if (isModalContextForMainMenu(context)) {
      LOG.debug { "The popups are closed, but the data context is a modal context. The main menu does not perform $action" }
      return@launch
    }
    val presentationFactory = PresentationFactory()
    Utils.expandActionGroupSuspend(DefaultActionGroup(action), presentationFactory, context, place, ActionUiKind.MAIN_MENU, false)
    performAction(action, place, presentationFactory.getPresentation(action), context)
  }
}

/**
 * Returns `true` when the main menu must not perform an action that is not enabled in a modal context.
 * The check uses the window of the context component, as [IdeKeyEventDispatcher] does for a shortcut.
 * So a popup window is a modal context only when its popup is a modal context.
 */
private fun isModalContextForMainMenu(context: DataContext): Boolean {
  if (context.getData(PlatformCoreDataKeys.IS_MODAL_CONTEXT) != true) {
    return false
  }
  val window = ComponentUtil.getWindow(context.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)) ?: return true
  return IdeKeyEventDispatcher.isModalContext(window)
}