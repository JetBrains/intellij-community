// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.highlighting

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile

class KotlinScriptEditorListener : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val ktFile = file.findPsiFile(source.project) as? KtFile ?: return
        if (ktFile.name.endsWith(KotlinFileType.DOT_SCRIPT_EXTENSION)) {
            KotlinScriptResolutionService.getInstance(source.project).launchProcessing(ktFile)
        }
    }
}
