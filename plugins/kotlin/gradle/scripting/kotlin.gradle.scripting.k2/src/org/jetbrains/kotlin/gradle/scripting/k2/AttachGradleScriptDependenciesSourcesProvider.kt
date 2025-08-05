// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import KotlinGradleScriptingBundle
import com.intellij.codeInsight.AttachSourcesProvider
import com.intellij.codeInsight.AttachSourcesProvider.AttachSourcesAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.gradle.scripting.shared.KotlinGradleScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.v1.indexSourceRootsEagerly

class AttachGradleScriptDependenciesSourcesProvider() : AttachSourcesProvider {
    override fun isApplicable(orderEntries: List<LibraryOrderEntry?>, psiFile: PsiFile): Boolean {
        val project = psiFile.project
        val virtualFile = psiFile.virtualFile

        if (GradleScriptIndexSourcesStorage.isIndexed(project) || indexSourceRootsEagerly()) return false
        return virtualFile.isGradleScriptDependency(project)
    }

    override fun getActions(
        orderEntries: List<LibraryOrderEntry>,
        psiFile: PsiFile
    ): Collection<AttachSourcesAction> {
        val project = psiFile.project

        val action = object : AttachSourcesAction {
            override fun getName(): String = KotlinGradleScriptingBundle.message("attach.dependencies.sources")

            override fun getBusyText(): String = KotlinGradleScriptingBundle.message("attaching.dependencies.sources")

            override fun perform(orderEntires: List<LibraryOrderEntry>): ActionCallback {
                val callback = ActionCallback()
                GradleScriptIndexSourcesStorage.getInstance(project).saveProjectIndexed()
                GradleScriptRefinedConfigurationProvider.getInstance(project).updateWorkspaceModel(callback)
                return callback
            }
        }
        return listOf(action)
    }

    private fun VirtualFile.isGradleScriptDependency(project: Project): Boolean {
        val workspaceModel = WorkspaceModel.getInstance(project)
        val index = workspaceModel.currentSnapshot.getVirtualFileUrlIndex()
        val fileUrlManager = workspaceModel.getVirtualFileUrlManager()


        var currentFile: VirtualFile? = this
        while (currentFile != null) {
            val entities = index.findEntitiesByUrl(fileUrlManager.getOrCreateFromUrl(currentFile.url))
            if (entities.none()) {
                currentFile = currentFile.parent
                continue
            }

            return entities.firstOrNull { it.entitySource is KotlinGradleScriptEntitySource } != null
        }

        return false
    }
}

