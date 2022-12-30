package org.jetbrains.completion.full.line.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import org.jetbrains.completion.full.line.settings.FullSettingsDialog

class ShowSettingsAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val settingsDialog = FullSettingsDialog()
    settingsDialog.show()
  }
}
