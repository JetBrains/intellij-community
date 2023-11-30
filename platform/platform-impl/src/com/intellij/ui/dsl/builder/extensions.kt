// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.PopupUtil
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

// This file contains extension functions that relates to platform-impl package only.
// Common platform related functionality should be put in correspondent module


fun Row.actionButton(action: AnAction, @NonNls actionPlace: String = ActionPlaces.UNKNOWN): Cell<ActionButton> {
  val component = ActionButton(action, action.templatePresentation.clone(), actionPlace, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE)
  return cell(component)
}

/**
 * Creates an [ActionButton] with [icon] and menu with provided [actions]
 */
fun Row.actionsButton(vararg actions: AnAction,
                      @NonNls actionPlace: String = ActionPlaces.UNKNOWN,
                      icon: Icon = AllIcons.General.GearPlain): Cell<ActionButton> {
  val actionGroup = PopupActionGroup(arrayOf(*actions))
  actionGroup.templatePresentation.icon = icon
  return cell(ActionButton(actionGroup, actionGroup.templatePresentation.clone(), actionPlace, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE))
}

private class PopupActionGroup(private val actions: Array<AnAction>) : ActionGroup(), DumbAware {
  init {
    isPopup = true
    templatePresentation.isPerformGroup = actions.isNotEmpty()
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> = actions

  override fun actionPerformed(e: AnActionEvent) {
    val popup = JBPopupFactory.getInstance().createActionGroupPopup(null, this, e.dataContext,
                                                                    JBPopupFactory.ActionSelectionAid.MNEMONICS, true)
    PopupUtil.showForActionButtonEvent(popup, e)
  }
}
