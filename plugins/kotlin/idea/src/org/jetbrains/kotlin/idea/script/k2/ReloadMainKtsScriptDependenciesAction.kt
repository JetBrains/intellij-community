// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script.k2

import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.ui.EditorNotifications
import org.jetbrains.kotlin.idea.core.script.alwaysVirtualFile
import org.jetbrains.kotlin.idea.core.script.k2.DefaultScriptResolutionStrategy
import org.jetbrains.kotlin.psi.KtFile
import java.util.concurrent.ConcurrentHashMap

fun KtFile.getScriptAnnotationsList(): List<String> = annotationEntries.map { it.text }.sorted()

internal class ReloadMainKtsScriptDependenciesAction : AnAction() {
    private val annotations = ConcurrentHashMap<KtFile, List<String>>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val ktFile = getKotlinScriptFile(editor) ?: return

        DefaultScriptResolutionStrategy.getInstance(project).execute(ktFile)
        annotations[ktFile] = runReadAction { ktFile.getScriptAnnotationsList() }
        EditorNotifications.getInstance(project).updateNotifications(ktFile.alwaysVirtualFile)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor != null && getNotificationVisibility(editor)
    }

    private fun getNotificationVisibility(editor: Editor): Boolean {
        val file = getKotlinScriptFile(editor) ?: return false
        if (!file.name.endsWith(".main.kts")) return false
        if (DiffUtil.isDiffEditor(editor)) return false

        val actualAnnotations = file.getScriptAnnotationsList()

        val previous = annotations[file] ?: emptyList()

        return if (previous.isEmpty() && actualAnnotations.isEmpty()) {
            false
        } else {
            previous != actualAnnotations
        }
    }

    private fun getKotlinScriptFile(editor: Editor): KtFile? {
        val virtualFile = editor.virtualFile ?: return null
        return editor.project?.let { virtualFile.findPsiFile(it) as? KtFile }
    }
}
