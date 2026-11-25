// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox.components

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.devkit.uiDsl.sandbox.UISandboxPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.InplaceButton
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class InplaceButtonPanel : UISandboxPanel {

  override val title: String = "InplaceButton"

  override fun createContent(disposable: Disposable): JComponent {
    return panel {
      row {
        text(DevkitUiDslBundle.message("sandbox.inplace.button.description"))
      }

      group(DevkitUiDslBundle.message("sandbox.border.title.states")) {
        row {
          text(DevkitUiDslBundle.message("sandbox.inplace.button.default.note"))
        }
        row(DevkitUiDslBundle.message("sandbox.inplace.button.label.default")) {
          cell(createButton())
        }
        row(DevkitUiDslBundle.message("sandbox.inplace.button.label.inactive")) {
          cell(createButton().apply { setActive(false) })
        }
      }
    }
  }

  private fun createButton(): InplaceButton {
    val icons = IconButton(DevkitUiDslBundle.message("sandbox.inplace.button.tooltip"),
                           AllIcons.Actions.Close,
                           AllIcons.Actions.CloseHovered,
                           IconLoader.getDisabledIcon(AllIcons.Actions.Close))
    return InplaceButton(icons) {}
  }
}
