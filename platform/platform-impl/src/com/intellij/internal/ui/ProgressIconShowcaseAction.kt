// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.ColorPicker
import com.intellij.ui.SpinningProgressIcon
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.getIconColor
import javax.swing.UIManager

/**
 * @author Konstantin Bulenkov
 */
class ProgressIconShowcaseAction: DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val panel = panel {
      row {
        icon(SpinningProgressIcon())
        link("Change color") {
          ColorPicker.showColorPickerPopup(null, getIconColor()) { color, _ ->
            if (color != null) {
              UIManager.put("ProgressIcon.color", color)
            }
          }
        }
      }
    }
    val dialog = dialog(templatePresentation.text, panel)
    dialog.isModal = false
    dialog.setSize(600, 600)
    dialog.show()
  }
}


