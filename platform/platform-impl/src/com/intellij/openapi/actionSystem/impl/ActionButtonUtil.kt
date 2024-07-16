package com.intellij.openapi.actionSystem.impl

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

object ActionButtonUtil {
  @JvmStatic
  fun findActionButton(component: JComponent,
                       condition: (ActionButton) -> Boolean): ActionButton? {
    return UIUtil.uiTraverser(component)
      .filter(ActionButton::class.java)
      .filter { condition(it) }
      .first()
  }

  @JvmStatic
  fun findToolbarActionButton(toolbar: ActionToolbar, condition: (ActionButton) -> Boolean): ActionButton? {
    return findActionButton(toolbar.component, condition)
  }

  @JvmStatic
  fun findActionButtonById(component: JComponent, actionId: String): ActionButton? {
    return findActionButton(component) { button ->
      ActionManager.getInstance().getId(button.action) == actionId
    }
  }

  @JvmStatic
  fun findToolbarActionButtonById(toolbar: ActionToolbar, actionId: String): ActionButton? {
    return findActionButtonById(toolbar.component, actionId)
  }
}