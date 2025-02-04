// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ex.ActionUtil.getActionGroup
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import java.awt.Dimension
import javax.swing.Action

internal fun SettingsDialog.createEditorToolbar(actions: List<Action>): DialogPanel {
  val actionGroup = getActionGroup("Back", "Forward");
  val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.SETTINGS_HISTORY, actionGroup!!, true)
  val search = (editor as SettingsEditor).getSearch()
  search.preferredSize = Dimension(400, search.preferredSize.height)

  return panel {
    row {
      cell(toolbar.component)
      cell(search).resizableColumn()
      for (i in 0..actions.size-1) {
        val button = DialogWrapper.createJButtonForAction(actions[i], rootPane)
        val gaps = if (i==actions.size-1) UnscaledGaps(right = 16) else UnscaledGaps(right = 8)
        cell(button).align(AlignX.RIGHT).customize(gaps)
      }
    }
  }
}