// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fe10.highlighting.suspender

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import org.jetbrains.kotlin.idea.base.highlighting.KotlinBaseHighlightingBundle
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import java.util.function.Function
import javax.swing.JComponent

internal class KotlinHighlightingSuspendNotificationProvider : EditorNotificationProvider {
    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (!file.isKotlinFileType()) {
            return null
        }

        if (!KotlinHighlightingSuspender.getInstance(project).isSuspended(file)) return null

        return Function {
            EditorNotificationPanel(it, EditorNotificationPanel.Status.Warning).apply {
                text = KotlinBaseHighlightingBundle.message("highlighting.for.0.is.suspended", file.name)
                createActionLabel(KotlinBaseHighlightingBundle.message("highlighting.action.text.ignore")) {
                    KotlinHighlightingSuspender.getInstance(project).unsuspend(file)
                }
            }
        }
    }
}