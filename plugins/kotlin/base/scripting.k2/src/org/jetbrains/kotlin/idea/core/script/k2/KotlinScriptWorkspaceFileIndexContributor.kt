// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesWorkspaceModelNode
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesWorkspaceModelNodesProvider
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.java.workspace.fileIndex.JvmPackageRootData
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleOrLibrarySourceRootData
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.KotlinScriptEntity
import org.jetbrains.kotlin.idea.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.k2.configurations.relativeLocation

class KotlinScriptWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<KotlinScriptLibraryEntity> {
    override val entityClass: Class<KotlinScriptLibraryEntity>
        get() = KotlinScriptLibraryEntity::class.java

    override fun registerFileSets(entity: KotlinScriptLibraryEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {

        entity.classes.forEach {
            registrar.registerFileSet(it, WorkspaceFileKind.EXTERNAL, entity, RootData)
        }

        entity.sources.forEach {
            registrar.registerFileSet(it, WorkspaceFileKind.EXTERNAL_SOURCE, entity, RootSourceData)
        }
    }

    private object RootData : JvmPackageRootData
    private object RootSourceData : JvmPackageRootData, ModuleOrLibrarySourceRootData
}


class KotlinScriptExternalLibrariesNodesProvider : ExternalLibrariesWorkspaceModelNodesProvider<KotlinScriptEntity> {
    override fun getWorkspaceClass(): Class<KotlinScriptEntity> = KotlinScriptEntity::class.java

    override fun createNode(entity: KotlinScriptEntity, project: Project, settings: ViewSettings?): AbstractTreeNode<*>? {
        val dependencies = entity.dependencies.flatMap { it.classes }.mapNotNull { it.virtualFile }.toList()

        val scriptFile = entity.virtualFileUrl.virtualFile ?: return null
        val scriptFileName = scriptFile.relativeLocation(project)

        return ExternalLibrariesWorkspaceModelNode(
            project, dependencies, emptyList(), "Script: $scriptFileName", KotlinIcons.SCRIPT, settings
        )
    }
}
