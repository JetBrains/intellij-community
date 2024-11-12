// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.script.k2

import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFileBase
import com.intellij.ui.EditorNotifications
import org.jetbrains.kotlin.idea.core.script.k2.BaseScriptModel
import org.jetbrains.kotlin.idea.core.script.k2.DependentScriptConfigurationsSource
import org.jetbrains.kotlin.idea.core.script.k2.ScriptConfigurationsProviderImpl
import org.jetbrains.kotlin.idea.core.script.scriptConfigurationsSourceOfType
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptConfigurationsProvider
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import java.util.concurrent.ConcurrentHashMap

fun KtFile.getScriptAnnotationsList(): List<String> = annotationEntries.map { it.text }.sorted()

internal class ReloadDependenciesScriptAction : AnAction() {
    val previousAnnotations = ConcurrentHashMap<VirtualFile, List<String>>()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = getKotlinScriptFile(editor) ?: return
        val dependencyResolutionService = project.service<DependencyResolutionService>()

        dependencyResolutionService.resolveInBackground {
            project.scriptConfigurationsSourceOfType<DependentScriptConfigurationsSource>()?.updateDependenciesAndCreateModules(
                listOf(BaseScriptModel(file))
            )

            previousAnnotations[file] = readAction {
                PsiManager.getInstance(project).findFile(file)?.safeAs<KtFile>()?.getScriptAnnotationsList() ?: emptyList()
            }

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
        val file = getKotlinScriptFile(editor) ?: return false

        val configSource =
            ScriptConfigurationsProvider.getInstance(project).safeAs<ScriptConfigurationsProviderImpl>()?.getConfigurationsSource(file)
        if (configSource !is DependentScriptConfigurationsSource) {
            return false
        }

        val actualAnnotations = PsiManager.getInstance(project).findFile(file)?.safeAs<KtFile>()?.getScriptAnnotationsList() ?: emptyList()

        val fileAnnotations = previousAnnotations[file] ?: emptyList()
        return fileAnnotations.isEmpty() || actualAnnotations != fileAnnotations
    }
}

private fun getKotlinScriptFile(editor: Editor): VirtualFile? {
    val virtualFile = editor.virtualFile ?: return null
    val ktFile = editor.project?.let { virtualFile.findPsiFile(it) as? KtFile } ?: return null

    return virtualFile.takeIf {
        it !is LightVirtualFileBase
                && it.isValid
                && ktFile.isScript()
    }
}
