// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotificationProvider.CONST_NULL
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinIdeaAnalysisBundle
import java.util.function.Function
import javax.swing.JComponent

class KotlinHighlightingSuspendNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?> {
        if (file.extension != KotlinFileType.EXTENSION && !FileTypeRegistry.getInstance().isFileOfType(file, KotlinFileType.INSTANCE)) {
            return CONST_NULL
        }

        if (!KotlinHighlightingSuspender.getInstance(project).isSuspended(file)) return CONST_NULL

        return Function {
            EditorNotificationPanel(it).apply {
                text = KotlinIdeaAnalysisBundle.message("highlighting.for.0.is.suspended", file.name)
                createActionLabel(KotlinIdeaAnalysisBundle.message("highlighting.action.text.ignore")) {
                    KotlinHighlightingSuspender.getInstance(project).unsuspend(file)
                }
            }
        }
    }
}