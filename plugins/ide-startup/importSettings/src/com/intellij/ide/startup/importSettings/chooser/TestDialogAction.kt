// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.HorizontalLayout
import javax.swing.*

class TestDialogAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val group = DefaultActionGroup()
    group.isPopup = false
    group.add(createAction(AllIcons.Actions.Pause, "IntelliJ IDEA", {}))
    group.add(createAction(AllIcons.Actions.Rerun, "Visual Studio Code", {}))

    val dialog = TestDialog(group, createLinkAction(AllIcons.Actions.BuildLoadChanges, "IntelliJ IDEA", {}))
    dialog.isModal = false
    //dialog.isResizable = false
    dialog.show()
    dialog.pack()
  }

  private fun createAction(icon: Icon, name: String, callBack: () -> Unit): AnAction {
    return object : DumbAwareAction({name}, icon) {
      override fun displayTextInToolbar(): Boolean {
        return true
      }

      override fun actionPerformed(e: AnActionEvent) {
        callBack()
      }

    }

  }

  private fun createLinkAction(icon: Icon, name: String, callBack: () -> Unit): AnAction {
    return object : DumbAwareAction({name}, icon), CustomComponentAction {

      override fun actionPerformed(e: AnActionEvent) {
        callBack()
      }

      override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
        val pane = JPanel().apply{
          add(JPanel(HorizontalLayout(0, SwingConstants.CENTER)).apply {
          add(LinkLabel<Unit>("Other Options", null))
          add(JLabel(AllIcons.General.ChevronDown))
        })}
        return pane
      }

    }

  }
}