// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.icons.AllIcons
import com.intellij.ide.setToolTipText
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.wm.impl.DefaultToolbarSplitButtonModel
import com.intellij.openapi.wm.impl.ToolbarSplitButton
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class ToolbarSplitButtonPanel : UISandboxPanel {

  override val title: String = "ToolbarSplitButton"

  override val isInternalApi: Boolean = true

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.toolbar.split.button.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.toolbar.split.button.group.default")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.toolbar.split.button.default.note"))
        }
        row {
          cell(createSplitButton())
        }
      }

      group(DevkitUiDslBundle.message("sandbox.toolbar.split.button.group.disabled")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.toolbar.split.button.disabled.note"))
        }
        row {
          cell(createSplitButton().apply { isEnabled = false })
        }
      }
    }
  }

  private fun createSplitButton(): ToolbarSplitButton {
    val configurations = listOf(
      DevkitUiDslBundle.message("sandbox.toolbar.split.button.configuration.application"),
      DevkitUiDslBundle.message("sandbox.toolbar.split.button.configuration.tests"),
      DevkitUiDslBundle.message("sandbox.toolbar.split.button.configuration.server"),
    )
    val model = DefaultToolbarSplitButtonModel()
    val button = ToolbarSplitButton(model).apply {
      text = configurations.first()
      leftIcons = listOf(AllIcons.Actions.Execute)
      setToolTipText(HtmlChunk.text(DevkitUiDslBundle.message("sandbox.toolbar.split.button.tooltip")))
    }

    model.addExpandListener {
      model.setExpandButtonSelected(true)
      val popup = JBPopupFactory.getInstance()
        .createPopupChooserBuilder(configurations)
        .setItemChosenCallback { button.text = it }
        .createPopup()
      popup.setFinalRunnable { model.setExpandButtonSelected(false) }
      popup.showUnderneathOf(button)
    }
    return button
  }
}
