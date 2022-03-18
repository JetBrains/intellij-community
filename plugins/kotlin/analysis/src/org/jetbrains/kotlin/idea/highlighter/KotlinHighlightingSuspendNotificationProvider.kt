// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.highlighter

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.KotlinIdeaAnalysisBundle

class KotlinHighlightingSuspendNotificationProvider(private val project: Project) :
    EditorNotifications.Provider<EditorNotificationPanel>() {

    override fun getKey(): Key<EditorNotificationPanel> = KEY

    override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor): EditorNotificationPanel? {
        if (file.extension != KotlinFileType.EXTENSION && !FileTypeRegistry.getInstance().isFileOfType(file, KotlinFileType.INSTANCE)) return null

        if (!KotlinHighlightingSuspender.getInstance(project).isSuspended(file)) return null

        return EditorNotificationPanel(fileEditor).apply {
            text = KotlinIdeaAnalysisBundle.message("highlighting.for.0.is.suspended", file.name)
            createActionLabel(KotlinIdeaAnalysisBundle.message("highlighting.action.text.ignore")) {
                KotlinHighlightingSuspender.getInstance(project).unsuspend(file)
            }
        }
    }

    companion object {
        private val KEY = Key.create<EditorNotificationPanel>("KotlinHighlightingSuspend")
    }
}