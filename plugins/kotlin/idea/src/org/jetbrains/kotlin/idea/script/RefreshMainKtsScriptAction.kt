// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script

import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFileBase
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.analysis.providers.analysisMessageBus
import org.jetbrains.kotlin.analysis.providers.topics.KotlinTopics
import org.jetbrains.kotlin.idea.core.script.K2ScriptDependenciesProvider
import org.jetbrains.kotlin.idea.core.script.ScriptModel
import org.jetbrains.kotlin.idea.core.script.createScriptModules
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import java.util.concurrent.ConcurrentHashMap
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.valueOrNull

internal class RefreshMainKtsScriptAction : DumbAwareAction() {

    private val lastModifiedPerScript = ConcurrentHashMap<VirtualFile, Long>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = getKotlinScriptFile(editor) ?: return

        val configurationResult = K2ScriptDependenciesProvider.getInstance(project)
            .addConfiguration(VirtualFileScriptSource(file))

        if (configurationResult is ResultWithDiagnostics.Failure) {
            configurationResult.reports
        }

        val configuration = configurationResult.valueOrNull()

        val model =
            ScriptModel(file, configuration?.dependenciesClassPath?.map { it.absolutePath } ?: emptyList())

        GlobalScope.launch {
            project.createScriptModules(setOf(model))

            writeAction {
                project.analysisMessageBus.syncPublisher(KotlinTopics.GLOBAL_MODULE_STATE_MODIFICATION).onModification()
            }
        }

        lastModifiedPerScript[file] = file.modificationStamp
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

private fun isMainKtsScript(virtualFile: VirtualFile) = virtualFile.name.endsWith(".main.kts")
