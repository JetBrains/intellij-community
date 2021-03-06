// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl.segmentedActionBar

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.beans.PropertyChangeListener
import javax.swing.JComponent

open class PillActionComponent : AnAction(), CustomComponentAction, DumbAware {
  companion object {
    const val PILL_SHOWN = "PILL_SHOWN"
  }

  private val group: ActionGroup = object : ActionGroup() {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      val actions = mutableListOf<AnAction>()
      actionGroup?.let {
        actions.add(it)
      }
      return actions.toTypedArray()
    }
  }

  protected var actionGroup: ActionGroup? = null
    set(value) {
      val bla = field == null && value != null

      field = value
      if (bla) {
        ActionToolbarImpl.updateAllToolbarsImmediately()
      }
    }

  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = actionGroup != null
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object : ActionToolbarImpl(ActionPlaces.NAVIGATION_BAR_TOOLBAR, group, true) {

      init {
        setForceMinimumSize(true)
        layoutPolicy = NOWRAP_LAYOUT_POLICY
      }

      private val presentationSyncer: PropertyChangeListener = PropertyChangeListener { evt ->
        val propertyName = evt.propertyName
        if (PILL_SHOWN == propertyName) {
          component.border = if (evt.newValue == true)
            PillBorder(JBColor.namedColor("StateWidget.activeBackground", JBColor(0xFFCB44, 0xFFCB44)), 1)
          else
            JBUI.Borders.empty()

        }
      }

      override fun addNotify() {
        super.addNotify()
        presentation.addPropertyChangeListener(presentationSyncer)
      }

      override fun removeNotify() {
        presentation.removePropertyChangeListener(presentationSyncer)
        super.removeNotify()
      }
    }
  }

}