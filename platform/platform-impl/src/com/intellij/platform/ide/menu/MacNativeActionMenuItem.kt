// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.menu

import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.ActionPresentationDecorator
import com.intellij.openapi.actionSystem.impl.EMPTY_MENU_ACTION_ICON
import com.intellij.openapi.actionSystem.impl.PoppedIcon
import com.intellij.openapi.actionSystem.impl.actionholder.createActionRef
import com.intellij.openapi.actionSystem.impl.isEnterKeyStroke
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.application.TransactionGuardImpl
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.icons.getMenuBarIcon
import com.intellij.ui.mac.screenmenu.MenuItem
import java.awt.EventQueue
import java.awt.event.InputEvent
import javax.swing.Icon
import javax.swing.KeyStroke

internal class MacNativeActionMenuItem(action: AnAction,
                                       place: String,
                                       private val context: DataContext,
                                       isMnemonicEnabled: Boolean,
                                       insideCheckedGroup: Boolean,
                                       useDarkIcons: Boolean,
                                       presentation: Presentation) {
  @JvmField
  internal val menuItemPeer: MenuItem

  init {
    val isToggleable = action is Toggleable
    var isToggled = isToggleable && Toggleable.isSelected(presentation)

    val actionRef = createActionRef(action)
    menuItemPeer = MenuItem()

    updateIcon(presentation = presentation,
               action = action,
               useDarkIcons = useDarkIcons,
               isToggleable = isToggleable,
               isToggled = isToggled,
               insideCheckedGroup = insideCheckedGroup)

    val id = ActionManager.getInstance().getId(action)
    val shortcuts = if (id == null) action.shortcutSet.getShortcuts() else KeymapUtil.getActiveKeymapShortcuts(id).getShortcuts()
    var accelerator: KeyStroke? = null
    for (shortcut in shortcuts) {
      if (shortcut !is KeyboardShortcut) {
        continue
      }

      val firstKeyStroke = shortcut.firstKeyStroke
      // If the action has `Enter` shortcut, do not add it. Otherwise, user won't be able to choose any ActionMenuItem other than that
      if (!isEnterKeyStroke(firstKeyStroke)) {
        accelerator = firstKeyStroke
        if (KeymapUtil.isSimplifiedMacShortcuts()) {
          menuItemPeer.setAcceleratorText(KeymapUtil.getPreferredShortcutText(shortcuts))
        }
      }
      break
    }

    val text = ActionPresentationDecorator.decorateTextIfNeeded(action, presentation.getText(isMnemonicEnabled))
    menuItemPeer.setLabel(text, accelerator)
    menuItemPeer.setEnabled(presentation.isEnabled)
    menuItemPeer.setActionDelegate {
      // called on AppKit when user activates menu item
      if (isToggleable) {
        isToggled = !isToggled
        menuItemPeer.setState(isToggled)
      }
      EventQueue.invokeLater {
        if (actionRef.getAction().isEnabledInModalContext || context.getData(PlatformCoreDataKeys.IS_MODAL_CONTEXT) != true) {
          (TransactionGuard.getInstance() as TransactionGuardImpl).performUserActivity {
            performAction(action = actionRef.getAction(), context = context, place = place)
          }
        }
      }
    }
  }

  private fun updateIcon(presentation: Presentation,
                         action: AnAction,
                         useDarkIcons: Boolean,
                         isToggleable: Boolean,
                         isToggled: Boolean,
                         insideCheckedGroup: Boolean) {
    if (isToggleable && (insideCheckedGroup || !UISettings.getInstance().showIconsInMenus) || presentation.icon == null) {
      menuItemPeer.setState(isToggled)
      setIcon(menuItemPeer = menuItemPeer, action = action, icon = presentation.icon, useDarkIcons = useDarkIcons)
    }
    else if (UISettings.getInstance().showIconsInMenus) {
      var icon = presentation.icon
      if (isToggleable && isToggled) {
        icon = icon?.let { PoppedIcon(it, 16, 16) }
      }
      setIcon(
        menuItemPeer = menuItemPeer,
        icon = if (presentation.isEnabled) icon else presentation.disabledIcon ?: icon?.let { IconLoader.getDisabledIcon(it) },
        action = action,
        useDarkIcons = useDarkIcons,
      )
    }
  }
}

private fun setIcon(menuItemPeer: MenuItem, icon: Icon?, useDarkIcons: Boolean, action: AnAction) {
  val effectiveIcon = when {
                        ActionMenu.isShowNoIcons(action) -> null
                        !ActionMenu.isAligned || !ActionMenu.isAlignedInGroup -> icon?.let { getMenuBarIcon(it, useDarkIcons) }
                        else -> icon ?: getMenuBarIcon(EMPTY_MENU_ACTION_ICON, useDarkIcons)
                      } ?: return
  menuItemPeer.setIcon(effectiveIcon)
}

private fun performAction(action: AnAction, context: DataContext, place: String) {
  val id = ActionManager.getInstance().getId(action)
  if (id != null) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("context.menu.click.stats.${id.replace(' ', '.')}")
  }
  IdeFocusManager.findInstanceByContext(context).runOnOwnContext(context, Runnable {
    val currentEvent = IdeEventQueue.getInstance().trueCurrentEvent
    val event = AnActionEvent(
      /* inputEvent = */ if (currentEvent is InputEvent) currentEvent else null,
      /* dataContext = */ context,
      /* place = */ place,
      /* presentation = */ action.getTemplatePresentation().clone(),
      /* actionManager = */ ActionManager.getInstance(),
      /* modifiers = */ 0,
      /* isContextMenuAction = */ true,
      /* isActionToolbar = */ false)
    if (ActionUtil.lastUpdateAndCheckDumb(action, event, false)) {
      ActionUtil.performActionDumbAwareWithCallbacks(action, event)
    }
  })
}