// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil.getActionGroup
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.actionButton
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.Action

internal fun SettingsDialog.createEditorToolbar(actions: List<Action>): DialogPanel {
  val actionGroup = getActionGroup("Back", "Forward");
  val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.SETTINGS_HISTORY, actionGroup!!, true)
  val settingsEditor = editor as SettingsEditor
  settingsEditor.search.preferredSize = Dimension(400, settingsEditor.search.preferredSize.height)
  val forceShowSidebar = AtomicBooleanProperty(false)

  val editorToolbar = panel {
    row {
      val action = object : DumbAwareAction({ ActionsBundle.message("action.SettingsEditor.ToggleSidebar.text") },
                                            AllIcons.General.LayoutEditorOnly) {
        override fun actionPerformed(e: AnActionEvent) {
          forceShowSidebar.set(!forceShowSidebar.get())
          repaint()
        }
      }
      val sidebarActionButton: Cell<ActionButton> = actionButton(action)
      sidebarActionButton.customize(UnscaledGaps(left = 8, right = 8))

      rootPane.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
          if (e == null)
            return
          if (rootPane.width < 600) {
            sidebarActionButton.component.isVisible = true
            settingsEditor.isSidebarVisible = forceShowSidebar.get()
          }
          else {
            sidebarActionButton.component.isVisible = false
            settingsEditor.isSidebarVisible = true
          }
        }
      })
      forceShowSidebar.afterChange {
        if (forceShowSidebar.get()) {
          sidebarActionButton.component.background = JBUI.CurrentTheme.ActionButton.pressedBackground()
          settingsEditor.isSidebarVisible = true
        }
        else {
          sidebarActionButton.component.background = null
          settingsEditor.isSidebarVisible = rootPane.width >= 600
        }
      }
      cell(toolbar.component)
      cell(settingsEditor.search).resizableColumn()
      for (i in 0..actions.size - 1) {
        val button = DialogWrapper.createJButtonForAction(actions[i], rootPane)
        val gaps = if (i == actions.size - 1) UnscaledGaps(right = 16) else UnscaledGaps(right = 8)
        cell(button).align(AlignX.RIGHT).customize(gaps)
      }
    }
  }
  return editorToolbar
}

