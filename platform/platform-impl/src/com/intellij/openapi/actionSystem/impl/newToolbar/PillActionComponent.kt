// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl.newToolbar

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.ui.FilledRoundedBorder
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import javax.swing.JComponent
import javax.swing.JPanel

open class PillActionComponent: AnAction(), CustomComponentAction {
  protected var actionGroup: ActionGroup? = null
    set(value) {
      field?.let { return }
      value ?: return

      field = value
      initialize(value)
    }

  private var panel: JComponent = JPanel()

  private fun initialize(group: ActionGroup) {
    val bar = ActionToolbarImpl(ActionPlaces.NAVIGATION_BAR_TOOLBAR, group, true)
    bar.setHideDisabled(true)
    panel = bar.component
  }

  fun showPill() {
    panel.border = FilledRoundedBorder(JBColor(0xFFCB44, 0xFFCB44), JBUI.scale(5), 0, DarculaUIUtil.LW.float)
  }

  fun hidePill() {
    panel.border = JBUI.Borders.empty()

  }

  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
   // showPill()
    return panel
  }

}