// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.ColorChooserService
import com.intellij.ui.SpinningProgressIcon
import com.intellij.ui.bigSpinningProgressIcon
import com.intellij.ui.components.dialog
import com.intellij.ui.dsl.builder.panel

/**
 * @author Konstantin Bulenkov
 */
internal class ProgressIconShowcaseAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    val icon = SpinningProgressIcon()
    val iconBig = bigSpinningProgressIcon()
    val panel = panel {
      row {
        icon(icon)
        link("Change color") {
          ColorChooserService.instance.showPopup(null, icon.getIconColor(), { color, _ -> color?.let {
            icon.setIconColor(it)
            iconBig.setIconColor(it)
          }})
        }
        icon(iconBig)
      }
    }
    val dialog = dialog(templatePresentation.text, panel)
    dialog.isModal = false
    dialog.setSize(600, 600)
    dialog.show()
  }
}


