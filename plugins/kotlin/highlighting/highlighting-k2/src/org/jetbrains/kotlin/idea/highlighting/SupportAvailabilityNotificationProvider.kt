// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import org.jetbrains.kotlin.idea.codeinsight.utils.KotlinSupportAvailability
import java.util.function.Function
import javax.swing.JComponent

class SupportAvailabilityNotificationProvider: EditorNotificationProvider, DumbAware {
    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? =
        KotlinSupportAvailability.EP_NAME.extensionList.firstOrNull { !it.isSupported(project, file) }?.let { availability ->
            Function {
                EditorNotificationPanel(it, EditorNotificationPanel.Status.Warning).apply {
                    text = K2HighlightingBundle.message("feature.is.not.fully.supported.0", availability.name())
                }
            }
        }
}