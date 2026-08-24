// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.core.script.scratch.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.fileEditor.TextEditor
import java.util.function.Supplier
import javax.swing.Icon
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.core.script.scratch.KotlinScratchFile
import org.jetbrains.kotlin.idea.core.script.scratch.ui.KotlinScratchFileEditorWithPreview
import org.jetbrains.kotlin.idea.core.script.scratch.ui.findScratchFileEditorWithPreview

abstract class ScratchAction(@Nls message: Supplier<String>, icon: Icon) : AnAction(message, message, icon) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = e.currentScratchFile != null
    }

    protected val AnActionEvent.currentScratchFile: KotlinScratchFile?
        get() = currentScratchEditor?.kotlinScratchFile

    protected val AnActionEvent.currentScratchEditor: KotlinScratchFileEditorWithPreview?
        get() {
            val textEditor = getData(PlatformCoreDataKeys.FILE_EDITOR) as? TextEditor
            return textEditor?.findScratchFileEditorWithPreview()
        }
}