// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl.segmentedActionBar

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

open class PillActionComponent: AnAction(), CustomComponentAction, DumbAware {
  companion object {
    const val PILL_SHOWN = "PILL_SHOWN"
  }

  protected var actionGroup: ActionGroup? = null

  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val pane: JComponent = JPanel(MigLayout("ins 0, novisualpadding, gap 0"))

    actionGroup?.let {
      val bar = object : ActionToolbarImpl(ActionPlaces.NAVIGATION_BAR_TOOLBAR, it, true) {
        private val presentationSyncer: PropertyChangeListener = PropertyChangeListener { evt ->
          val propertyName = evt.propertyName
          if (PILL_SHOWN == propertyName) {
            component.border = if (evt.newValue == true)
              PillBorder(JBColor(0xFFCB44, 0xFFCB44), 1)
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

      pane.add(bar.component)
    }

    pane.border = JBUI.Borders.empty(0, 2)
    return pane
  }

}