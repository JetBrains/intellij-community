// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.wm.impl.ExpandableComboAction
import com.intellij.openapi.wm.impl.SplitButtonAction
import com.intellij.openapi.wm.impl.ToolbarComboButton
import com.intellij.openapi.wm.impl.ToolbarSplitButton
import com.intellij.openapi.wm.impl.headertoolbar.createDemoToolbar
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JOptionPane
import javax.swing.JPanel

class ToolbarDemoAction: AnAction() {
  override fun actionPerformed(e: AnActionEvent) {

    val dialog = object : DialogWrapper(e.project, false, IdeModalityType.MODELESS) {
      override fun createCenterPanel(): JComponent {
        val toolbarCmp = createToolbar().component
        toolbarCmp.border = JBUI.Borders.customLine(JBColor.GRAY)
        toolbarCmp.background = JBColor.namedColor("MainToolbar.background", JBColor.PanelBackground)
        toolbarCmp.isOpaque = true

        return JPanel(BorderLayout()).apply { add(toolbarCmp, BorderLayout.NORTH) }
      }

      override fun createDefaultActions() {
        super.createDefaultActions()
        init()
      }
    }

    dialog.title = templateText
    dialog.setSize(400, 400)
    dialog.show()
  }

  private fun createToolbar(): ActionToolbar {
    val group = DefaultActionGroup()
    group.add(TestComboButtonAction())
    group.add(TestSplitButtonAction())

    val res = createDemoToolbar(group)
    return res
  }
}

private class TestSplitButtonAction: SplitButtonAction() {
  override fun createPopup(event: AnActionEvent): JBPopup {
    val step = BaseListPopupStep(null, "item 1", "item 2", "item 3")
    return JBPopupFactory.getInstance().createListPopup(step)
  }

  override fun actionPerformed(e: AnActionEvent) {
    JOptionPane.showMessageDialog(null, "Test action", "Test", JOptionPane.INFORMATION_MESSAGE)
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    (component as? ToolbarSplitButton)?.apply {
      text = "Split button"
      leftIcons = listOf(AllIcons.General.Mouse)
      //rightIcons = emptyList()
    }
  }
}

private class TestComboButtonAction: ExpandableComboAction() {

  override fun createPopup(event: AnActionEvent): JBPopup {
    val step = BaseListPopupStep(null, "item 1", "item 2", "item 3")
    return JBPopupFactory.getInstance().createListPopup(step)
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    (component as? ToolbarComboButton)?.apply {
      text = "Combo button"
      leftIcons = listOf(AllIcons.General.Filter)
      //rightIcons = emptyList()
    }
  }
}