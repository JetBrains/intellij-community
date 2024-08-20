// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script.k2

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.LightVirtualFileBase
import com.intellij.ui.EditorNotifications
import org.jetbrains.kotlin.idea.base.scripting.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.k2.BaseScriptModel
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import java.util.concurrent.ConcurrentHashMap

internal class ReloadDependenciesMainKtsScriptAction : AnAction() {

    private val lastModifiedPerScript = ConcurrentHashMap<VirtualFile, Long>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = getKotlinScriptFile(editor) ?: return

        runWithModalProgressBlocking(
            project,
            KotlinBaseScriptingBundle.message("progress.title.loading.script.dependencies")
        ) {
            MainKtsScriptDependenciesSource.getInstance(project)?.updateDependenciesAndCreateModules(
                listOf(BaseScriptModel(file))
            )

            ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()
            HighlightingSettingsPerFile.getInstance(project).incModificationCount()

            lastModifiedPerScript[file] = file.modificationStamp
            EditorNotifications.getInstance(project).updateNotifications(file)
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        e.presentation.isEnabledAndVisible = getNotificationVisibility(editor)
    }

    private fun getNotificationVisibility(editor: Editor): Boolean {
        if (DiffUtil.isDiffEditor(editor)) return false

        val project = editor.project ?: return false

        if (ScriptDefinitionProvider.getServiceIfCreated(project) == null) return false
        val file = getKotlinScriptFile(editor) ?: return false

        val oldValue = lastModifiedPerScript[file]

        return oldValue == null || oldValue < file.modificationStamp
    }
}

private fun getKotlinScriptFile(editor: Editor): VirtualFile? = FileDocumentManager.getInstance()
    .getFile(editor.document)
    ?.takeIf {
        it !is LightVirtualFileBase
                && it.isValid
                && it.isKotlinFileType()
                && isMainKtsScript(it)
    }
