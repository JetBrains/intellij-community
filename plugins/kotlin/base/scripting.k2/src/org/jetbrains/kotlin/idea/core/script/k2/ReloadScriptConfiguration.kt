// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.base.scripting.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.alwaysVirtualFile
import org.jetbrains.kotlin.idea.core.script.k2.configurations.getConfigurationResolver
import org.jetbrains.kotlin.idea.core.script.k2.highlighting.DefaultScriptResolutionStrategy
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptModuleManager.Companion.removeScriptModules
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition

internal class ReloadScriptConfiguration : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val ktFile = getProjectKtFile(e) ?: return

        ReloadScriptConfigurationService.getInstance(project).reloadScriptData(ktFile)
    }

    private fun getProjectKtFile(e: AnActionEvent): KtFile? {
        val project = e.project ?: return null
        val editor = e.getData(CommonDataKeys.EDITOR)
        return if (editor != null) {
            PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        } else {
            e.getData(CommonDataKeys.PSI_FILE)
        } as? KtFile
    }

    override fun update(e: AnActionEvent) {
        val ktFile = getProjectKtFile(e)
        if (ktFile != null && ktFile.name.endsWith(".kts")) {
            e.presentation.isEnabledAndVisible = true
            e.presentation.text = KotlinBaseScriptingBundle.message("reload.script.configuration.text", ktFile.name)
        } else {
            e.presentation.isEnabledAndVisible = false
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

@Service(Service.Level.PROJECT)
class ReloadScriptConfigurationService(private val project: Project, private val coroutineScope: CoroutineScope) {
    fun reloadScriptData(ktFile: KtFile) {
        val definition = ktFile.findScriptDefinition() ?: return

        coroutineScope.launch {
            definition.getConfigurationResolver(project).remove(ktFile.alwaysVirtualFile)
            project.removeScriptModules(listOf(ktFile.alwaysVirtualFile))
            DefaultScriptResolutionStrategy.getInstance(project).execute(ktFile).join()
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): ReloadScriptConfigurationService = project.service()
    }
}
