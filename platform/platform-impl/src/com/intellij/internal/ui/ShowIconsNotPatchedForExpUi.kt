// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import javax.swing.JList

class ShowIconsNotPatchedForExpUi : DumbAwareAction("Show Icons Non Patched For ExpUI") {
  override fun actionPerformed(e: AnActionEvent) {
    val data = ExperimentalUI.NotPatchedIconRegistry.getData().sortedBy { it.originalPath }.toTypedArray()
    val panel = panel {
      row {
        val list = JList(data)
        list.cellRenderer = SimpleListCellRenderer.create { label, model, _ ->
          label.icon = model.icon
          label.text = model.originalPath
        }
        scrollCell(list).align(Align.FILL)
      }.resizableRow()
    }
    dialog("Icons Non Patched For ExpUI", panel, resizable = true).show()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = Registry.`is`("ide.experimental.ui.dump.not.patched.icons")
  }
}