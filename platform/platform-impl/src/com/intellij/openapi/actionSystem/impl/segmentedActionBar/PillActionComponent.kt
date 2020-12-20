// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl.segmentedActionBar

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import javax.swing.JComponent
import javax.swing.JPanel

open class PillActionComponent: AnAction(), CustomComponentAction, DumbAware {
  protected var actionGroup: ActionGroup? = null
    set(value) {
      field?.let { return }
      value ?: return

      field = value
      initialize(value)
    }

  private var pane: JComponent = JPanel(MigLayout("ins 0, novisualpadding, gap 0"))
  private var component: JComponent = JPanel()

  private fun initialize(group: ActionGroup) {
    val bar = ActionToolbarImpl(ActionPlaces.NAVIGATION_BAR_TOOLBAR, group, true)
    this.component = bar.component

    pane.add(component)
    pane.border = JBUI.Borders.empty(0, 2)
  }

  fun showPill() {
    component.border = PillBorder(
      JBColor(0xFFCB44, 0xFFCB44), 1)

  }

  fun hidePill() {
    component.border = JBUI.Borders.empty()

  }

  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return pane
  }

}