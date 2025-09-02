// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryDependency
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import org.jetbrains.kotlin.idea.core.script.KotlinScriptEntitySource
import org.jetbrains.kotlin.idea.core.script.k2.configurations.MainKtsScriptConfigurationProvider

class MainKtsScriptDependenciesProvider : K2IdeScriptAdditionalIdeaDependenciesProvider {
    override fun getRelatedModules(
        file: VirtualFile,
        project: Project
    ): List<ModuleEntity> {
        val virtualFileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()

        MainKtsScriptConfigurationProvider.getInstance(project).getImportedScripts(file)
        val importedScriptUrls = MainKtsScriptConfigurationProvider.getInstance(project).getImportedScripts(file)
            .map { it.toVirtualFileUrl(virtualFileUrlManager) }.toSet()

        return project.workspaceModel.currentSnapshot.entitiesBySource {
            if (it is KotlinScriptEntitySource) it.virtualFileUrl in importedScriptUrls else false
        }.mapNotNull { it as? ModuleEntity }.toList()
    }

    override fun getRelatedLibraries(
        file: VirtualFile,
        project: Project
    ): List<LibraryDependency> {
        return getRelatedModules(file, project).flatMap { it.dependencies }.mapNotNull { it as? LibraryDependency }.distinct()
    }
}