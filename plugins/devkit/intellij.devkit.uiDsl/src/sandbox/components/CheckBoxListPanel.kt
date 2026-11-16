// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

private val PLUGINS: List<@NlsSafe String> = listOf("Git", "GitHub", "GitLab", "Docker", "Kubernetes", "Markdown", "Terraform", "YAML")
private val SELECTED_PLUGINS: Set<String> = setOf("Git", "Docker", "Markdown")
private const val VISIBLE_ROW_COUNT = 5

internal class CheckBoxListPanel : UISandboxPanel {

  override val title: String = "CheckBoxList"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.check.box.list.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.states")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.check.box.list.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.check.box.list.label.default")) {
          cell(JBScrollPane(createList()))
        }
        row(DevkitUiDslBundle.message("sandbox.check.box.list.label.disabled")) {
          cell(JBScrollPane(createList().apply { isEnabled = false }))
        }
      }
    }
  }

  private fun createList(): CheckBoxList<String> {
    return CheckBoxList<String>().apply {
      for (plugin in PLUGINS) {
        addItem(plugin, plugin, plugin in SELECTED_PLUGINS)
      }
      visibleRowCount = VISIBLE_ROW_COUNT
    }
  }
}
