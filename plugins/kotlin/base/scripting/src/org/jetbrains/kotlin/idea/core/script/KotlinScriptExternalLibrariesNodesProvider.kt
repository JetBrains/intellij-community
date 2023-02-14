// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.ExternalLibrariesWorkspaceModelNodesProvider
import com.intellij.ide.projectView.impl.nodes.SyntheticLibraryElementNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.virtualFile
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.core.script.ucache.*
import java.nio.file.Path
import javax.swing.Icon

class KotlinScriptExternalLibrariesNodesProvider: ExternalLibrariesWorkspaceModelNodesProvider<KotlinScriptEntity> {

    override fun getWorkspaceClass(): Class<KotlinScriptEntity> = KotlinScriptEntity::class.java

    override fun createNode(entity: KotlinScriptEntity, project: Project, settings: ViewSettings?): AbstractTreeNode<*>? {
        if (!scriptsAsEntities) return null

        val dependencies = entity.listDependencies(project)
        val path = entity.path
        val scriptFile = VirtualFileManager.getInstance().findFileByNioPath(Path.of(path))

        // If there is no local file with such path we don't want to show it in file tree view
        // One of the cases are injected scripts
        if (scriptFile == null) return null

        val scriptFileName = scriptFile.relativeName(project)
        val library = KotlinScriptDependenciesLibrary("Script: $scriptFileName",
                                                      dependencies.compiled, dependencies.sources)

        return SyntheticLibraryElementNode(project, library, library, settings)
    }
}


private data class KotlinScriptDependenciesLibrary(val name: String, val classes: Collection<VirtualFile>, val sources: Collection<VirtualFile>) :
    SyntheticLibrary(), ItemPresentation {

    override fun getBinaryRoots(): Collection<VirtualFile> = classes

    override fun getSourceRoots(): Collection<VirtualFile> = sources

    override fun getPresentableText(): String = name

    override fun getIcon(unused: Boolean): Icon = KotlinIcons.SCRIPT
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

@JvmInline
private value class ScriptDependencies(private val compiledAndSources: Pair<List<VirtualFile>, List<VirtualFile>>) {
    val compiled
        get() = compiledAndSources.first

    val sources
        get() = compiledAndSources.second
}