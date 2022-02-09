// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.ColorPicker
import com.intellij.ui.SpinningProgressIcon
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.panel

/**
 * @author Konstantin Bulenkov
 */
class ProgressIconShowcaseAction: DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val icon = SpinningProgressIcon()
    val panel = panel {
      row {
        icon(icon)
        link("Change color") {
          ColorPicker.showColorPickerPopup(null, icon.getIconColor()) { color, _ -> color?.let { icon.setIconColor(it) }}
        }
      }
    }
    val dialog = dialog(templatePresentation.text, panel)
    dialog.isModal = false
    dialog.setSize(600, 600)
    dialog.show()
  }
}


