// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import java.util.function.Function
import javax.swing.JComponent

class K2ScriptSupportNotificationProvider: EditorNotificationProvider, DumbAware {
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        if (!file.isKotlinFileType() || !KotlinScriptingSettings.getInstance(project).showK2SupportWarning) {
            return null
        }
        val ktFile = (file.toPsiFile(project) as? KtFile) ?: return null
        if (!ktFile.isScript()) return null

        return Function {
            EditorNotificationPanel(it, EditorNotificationPanel.Status.Warning).apply {
                text = K2HighlightingBundle.message("script.is.not.fully.supported")
                createActionLabel(K2HighlightingBundle.message("script.is.not.fully.supported.ignore")) {
                    KotlinScriptingSettings.getInstance(project).showK2SupportWarning = false
                    EditorNotifications.getInstance(project).updateAllNotifications()
                }
            }
        }
    }

}