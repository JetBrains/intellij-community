// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import org.jetbrains.kotlin.idea.KotlinScriptEntity
import org.jetbrains.kotlin.idea.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.k2.configurations.MainKtsScriptConfigurationProvider

class MainKtsScriptDependenciesProvider : K2IdeScriptAdditionalIdeaDependenciesProvider {
    override fun getRelatedModules(
        file: VirtualFile, project: Project
    ): List<VirtualFile> {
        return MainKtsScriptConfigurationProvider.getInstance(project).getImportedScripts(file)
    }

    override fun getRelatedLibraries(
        file: VirtualFile, project: Project
    ): List<KotlinScriptLibraryEntity> {
        val currentSnapshot = project.workspaceModel.currentSnapshot
        val index = currentSnapshot.getVirtualFileUrlIndex()
        val manager = project.workspaceModel.getVirtualFileUrlManager()

        return getRelatedModules(file, project).flatMap {
            index.findEntitiesByUrl(it.toVirtualFileUrl(manager))
        }.filterIsInstance<KotlinScriptEntity>().flatMap { it.dependencies.mapNotNull { currentSnapshot.resolve(it) } }
    }
}