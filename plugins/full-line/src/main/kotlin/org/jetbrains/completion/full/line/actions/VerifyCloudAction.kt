package org.jetbrains.completion.full.line.actions

import com.intellij.lang.Language
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.DumbAware
import org.jetbrains.completion.full.line.settings.FullSettingsDialog
import org.jetbrains.completion.full.line.settings.VerifyCloudDialog

class VerifyCloudAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val settingsDialog = VerifyCloudDialog()
        settingsDialog.show()
    }
}
