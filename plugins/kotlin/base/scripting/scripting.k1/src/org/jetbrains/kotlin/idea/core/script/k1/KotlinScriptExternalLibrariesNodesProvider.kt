// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesWorkspaceModelNode
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesWorkspaceModelNodesProvider
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.core.script.k1.ucache.relativeName
import org.jetbrains.kotlin.idea.core.script.k1.ucache.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k1.ucache.KotlinScriptLibraryRoot
import org.jetbrains.kotlin.idea.core.script.k1.ucache.KotlinScriptLibraryRootTypeId
import java.nio.file.Path

class KotlinScriptExternalLibrariesNodesProvider: ExternalLibrariesWorkspaceModelNodesProvider<KotlinScriptEntity> {

    override fun getWorkspaceClass(): Class<KotlinScriptEntity> = KotlinScriptEntity::class.java

    override fun createNode(entity: KotlinScriptEntity, project: Project, settings: ViewSettings?): AbstractTreeNode<*>? {
        val dependencies = entity.listDependencies(project)
        val path = entity.path
        val scriptFile = VirtualFileManager.getInstance().findFileByNioPath(Path.of(path))

        // If there is no local file with such path we don't want to show it in file tree view
        // One of the cases are injected scripts
        if (scriptFile == null) return null

        val scriptFileName = scriptFile.relativeName(project)
        return ExternalLibrariesWorkspaceModelNode(
            project, dependencies.compiled + dependencies.sources, emptyList(),
            "Script: $scriptFileName", KotlinIcons.SCRIPT, settings
        )
    }

    private fun KotlinScriptEntity.listDependencies(project: Project): ScriptDependencies {
        val storage = WorkspaceModel.getInstance(project).currentSnapshot

        fun List<KotlinScriptLibraryRoot>.files() = asSequence()
            .mapNotNull { it.url.virtualFile }
            .filter { it.isValid }
            .toList()

        val (compiledRoots, sourceRoots) = dependencies.asSequence()
            .map { storage.resolve(it) ?: error("Unresolvable library: ${it.name}, script=$path") }
            .flatMap { it.roots }
            .partition { it.type == KotlinScriptLibraryRootTypeId.COMPILED }

        return ScriptDependencies(Pair(compiledRoots.files(), sourceRoots.files()))
    }
}

@JvmInline
private value class ScriptDependencies(private val compiledAndSources: Pair<List<VirtualFile>, List<VirtualFile>>) {
    val compiled
        get() = compiledAndSources.first

    val sources
        get() = compiledAndSources.second
}