// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.icons.AllIcons
import com.intellij.ide.setToolTipText
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.impl.DefaultToolbarComboButtonModel
import com.intellij.openapi.wm.impl.ToolbarComboButton
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class ToolbarComboButtonPanel : UISandboxPanel {

  override val title: String = "ToolbarComboButton"

  override val isInternalApi: Boolean = true

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.toolbar.combo.button.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.toolbar.combo.button.group.default")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.toolbar.combo.button.default.note"))
        }
        row {
          cell(createComboButton())
        }
      }

      group(DevkitUiDslBundle.message("sandbox.toolbar.combo.button.group.disabled")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.toolbar.combo.button.disabled.note"))
        }
        row {
          cell(createComboButton().apply { isEnabled = false })
        }
      }
    }
  }

  private fun createComboButton(): ToolbarComboButton {
    val projects = listOf(
      DevkitUiDslBundle.message("sandbox.toolbar.combo.button.project.sandbox"),
      DevkitUiDslBundle.message("sandbox.toolbar.combo.button.project.sample"),
      DevkitUiDslBundle.message("sandbox.toolbar.combo.button.project.remote"),
    )
    val model = DefaultToolbarComboButtonModel()
    val button = ToolbarComboButton(model).apply {
      text = projects.first()
      leftIcons = listOf(AllIcons.Nodes.Folder)
      setToolTipText(HtmlChunk.text(DevkitUiDslBundle.message("sandbox.toolbar.combo.button.tooltip")))
      accessibleNamePrefix = DevkitUiDslBundle.message("sandbox.toolbar.combo.button.accessible.prefix")
    }

    model.addActionListener {
      model.setSelected(true)
      val popup = JBPopupFactory.getInstance()
        .createPopupChooserBuilder(projects)
        .setItemChosenCallback { button.text = it }
        .createPopup()
      popup.setFinalRunnable { model.setSelected(false) }
      popup.showUnderneathOf(button)
    }
    return button
  }
}
