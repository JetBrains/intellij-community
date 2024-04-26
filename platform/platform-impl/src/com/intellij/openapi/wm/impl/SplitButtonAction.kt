// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import java.awt.event.ActionEvent
import javax.swing.JComponent

abstract class SplitButtonAction : AnAction(), CustomComponentAction {

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val model = MyPopupModel()
    model.addActionListener { actionEvent -> buttonPressed(actionEvent, actionEvent.source as JComponent, presentation, place) }
    model.addExpandListener { actionEvent ->
      val combo = (actionEvent.source as? ToolbarSplitButton) ?: return@addExpandListener
      val dataContext = DataManager.getInstance().getDataContext(combo)
      val anActionEvent = AnActionEvent.createFromDataContext(place, presentation, dataContext)
      val popup = createPopup(anActionEvent) ?: return@addExpandListener
      popup.addListener(object : JBPopupListener {
        override fun beforeShown(event: LightweightWindowEvent) {
          model.isPopupShown = true
        }

        override fun onClosed(event: LightweightWindowEvent) {
          model.isPopupShown = false
        }
      })
      popup.showUnderneathOf(combo)
    }
    return ToolbarSplitButton(model)
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    super.updateCustomComponent(component, presentation)
    component.isEnabled = presentation.isEnabled
    (component as? AbstractToolbarCombo)?.let {
      it.text = presentation.text
      it.toolTipText = presentation.description
      val pLeftIcons = presentation.getClientProperty(ExpandableComboAction.LEFT_ICONS_KEY)
      val pRightIcons = presentation.getClientProperty(ExpandableComboAction.RIGHT_ICONS_KEY)
      it.leftIcons = when {
        pLeftIcons != null -> pLeftIcons
        !presentation.isEnabled && presentation.disabledIcon != null -> listOf(presentation.disabledIcon)
        presentation.icon != null -> listOf(presentation.icon)
        else -> emptyList()
      }
      if (pRightIcons != null) it.rightIcons = pRightIcons
    }
  }

  protected abstract fun createPopup(event: AnActionEvent): JBPopup?

  protected open fun buttonPressed(event: ActionEvent, widget: JComponent, presentation: Presentation, place: String) {
    val dataContext = DataManager.getInstance().getDataContext(widget)
    val anActionEvent = AnActionEvent.createFromDataContext(place, presentation, dataContext)
    actionPerformed(anActionEvent)
  }

  private class MyPopupModel: DefaultToolbarSplitButtonModel() {
    var isPopupShown: Boolean = false

    override fun isExpandButtonSelected(): Boolean {
      return super.isExpandButtonSelected() || isPopupShown
    }
  }

}