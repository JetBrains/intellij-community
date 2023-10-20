// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.testActions

import com.intellij.ide.startup.importSettings.chooser.settingChooser.SettingChooserDialog
import com.intellij.ide.startup.importSettings.chooser.ui.MultiplePageDialog
import com.intellij.ide.startup.importSettings.data.JBrActionsDataProvider
import com.intellij.ide.startup.importSettings.data.TestJbService
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class SettingChooserDialogAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    MultiplePageDialog.show(SettingChooserDialog(JBrActionsDataProvider.getInstance(), TestJbService.main), isModal = false)
  }
}

class SettingChooserSimpleDialogAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val dialog = SettingChooserDialog(JBrActionsDataProvider.getInstance(), TestJbService.main)
    dialog.isModal = false
    dialog.isResizable = false
    dialog.show()

    dialog.pack()
  }
}