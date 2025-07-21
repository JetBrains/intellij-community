// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1.ucache

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.BuilderSnapshot
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.applyIf
import org.jetbrains.kotlin.idea.core.script.v1.ScriptAdditionalIdeaDependenciesProvider
import org.jetbrains.kotlin.idea.core.script.k1.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependencyAware
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.relativeToOrNull

/**
 * This file contains functions to work with [WorkspaceModel] in the context of [KotlinScriptEntity].
 * For technical details, see [this article](https://jetbrains.team/p/wm/documents/Development/a/Workspace-model-custom-entities-creation).
 */

fun KotlinScriptEntity.listDependencies(project: Project, rootTypeId: KotlinScriptLibraryRootTypeId? = null): List<VirtualFile> {
    val storage = WorkspaceModel.getInstance(project).currentSnapshot
    return dependencies.asSequence()
        .mapNotNull { storage.resolve(it) }
        .flatMap { it.roots }
        .applyIf(rootTypeId != null) { filter { it.type == rootTypeId } }
        .mapNotNull { it.url.virtualFile }
        .filter { it.isValid }
        .toList()
}

fun VirtualFile.findDependentScripts(project: Project): List<KotlinScriptEntity>? {
    val workspaceModel = WorkspaceModel.getInstance(project)
    val storage = workspaceModel.currentSnapshot
    val index = storage.getVirtualFileUrlIndex()
    val fileUrlManager = workspaceModel.getVirtualFileUrlManager()

    var currentFile: VirtualFile? = this
    while (currentFile != null) {
        val entities = index.findEntitiesByUrl(fileUrlManager.getOrCreateFromUrl(currentFile.url))
        if (entities.none()) {
            currentFile = currentFile.parent
            continue
        }

        return entities
            .filterIsInstance<KotlinScriptLibraryEntity>()
            .flatMap { it.usedInScripts }
            .map { storage.resolve(it) ?: error("Unresolvable script: ${it.path}") }
            .toList()
    }
    return null
}

fun BuilderSnapshot.syncScriptEntities(
    project: Project,
    scriptFilesToAddOrUpdate: List<VirtualFile>,
    scriptFilesToRemove: List<VirtualFile>
) {
    builder.syncScriptEntities(scriptFilesToAddOrUpdate, scriptFilesToRemove, project)

/*
    // Use these ancillary functions for troubleshooting, they allow seeing resulting scripts-to-libraries (and vice versa) relations.
    val scriptsDebugInfo = project.scriptsDebugInfo();
    val scriptLibrariesDebugInfo = project.scriptLibrariesDebugInfo();
    Unit // <= toggle breakpoint here and enjoy
*/
}

private fun MutableEntityStorage.syncScriptEntities(
    scriptFilesToAddOrUpdate: List<VirtualFile>,
    scriptFilesToRemove: List<VirtualFile>,
    project: Project
) {
    if (scriptFilesToRemove.isNotEmpty()) {
        removeOutdatedScripts(scriptFilesToRemove.map { it.path })
    }

    scriptFilesToAddOrUpdate.forEach { scriptFile ->
        val actualLibraries = getActualScriptLibraries(scriptFile, project)
        if (actualLibraries.isEmpty()) {
            return@forEach
        }

        val actualLibRefs = actualLibraries.map { it.symbolicId }.toSet()
        val scriptEntity = resolve(KotlinScriptId(scriptFile.path))
            ?: addNewScriptEntity(scriptFile, actualLibRefs, project)

        removeOutdatedLibraries(scriptEntity, actualLibraries)

        val currentLibRefs = scriptEntity.dependencies
        val refsToAdd = actualLibRefs - currentLibRefs
        val refsToRemove = currentLibRefs - actualLibRefs

        if (refsToAdd.isNotEmpty() || refsToRemove.isNotEmpty()) {
            modifyKotlinScriptEntity(scriptEntity) {
                dependencies.removeAll(refsToRemove)
                dependencies.addAll(refsToAdd)
            }
        }

        actualLibraries.forEach {
            if (!it.usedInScripts.contains(scriptEntity.symbolicId)) {
                modifyKotlinScriptLibraryEntity(it) {
                    usedInScripts.add(scriptEntity.symbolicId)
                }
            }
        }
    }
}

private fun MutableEntityStorage.removeOutdatedLibraries(
    existingScriptEntity: KotlinScriptEntity,
    actualLibraries: List<KotlinScriptLibraryEntity>
) {
    val currentLibraries = entities(KotlinScriptLibraryEntity::class.java)
        .filter { it.usedInScripts.contains(existingScriptEntity.symbolicId) }
        .toSet()

    val outdatedLibraries = currentLibraries - actualLibraries.toSet()

    outdatedLibraries.forEach {
        if (it.usedInScripts.size == 1) {
            removeEntity(it)
        } else {
            modifyKotlinScriptLibraryEntity(it) {
                usedInScripts.remove(existingScriptEntity.symbolicId)
            }
        }
    }
}

private fun MutableEntityStorage.addNewScriptEntity(
    scriptFile: VirtualFile,
    libraryRefs: Set<KotlinScriptLibraryId>,
    project: Project
): KotlinScriptEntity {
    val fileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
    val scriptSource = KotlinScriptEntitySource(scriptFile.toVirtualFileUrl(fileUrlManager))
    val scriptEntity = KotlinScriptEntity.Companion(scriptFile.path, libraryRefs, scriptSource)
    return addEntity(scriptEntity)
}

private fun MutableEntityStorage.removeOutdatedScripts(removedScriptPaths: List<String>) {
    removedScriptPaths
        .mapNotNull { resolve(KotlinScriptId(it)) }
        .forEach { outdatedScript ->
            outdatedScript.dependencies
                .mapNotNull { it.resolve(this) }
                .forEach {
                    if (it.usedInScripts.size == 1) {
                        removeEntity(it)
                    } else {
                        modifyKotlinScriptLibraryEntity(it) {
                            usedInScripts.remove(outdatedScript.symbolicId)
                        }
                    }
                }
            removeEntity(outdatedScript)
        }
}

private fun MutableList<KotlinScriptLibraryEntity.Builder>.fillWithFiles(
    project: Project,
    classFiles: Collection<VirtualFile>,
    sourceFiles: Collection<VirtualFile>
) {
    classFiles.forEach {
        add(project.createLibraryEntity(it.presentableUrl, it, KotlinScriptLibraryRootTypeId.COMPILED))
    }
    sourceFiles.forEach {
        add(project.createLibraryEntity(it.presentableUrl, it, KotlinScriptLibraryRootTypeId.SOURCES))
    }
}

private fun MutableEntityStorage.getActualScriptLibraries(scriptFile: VirtualFile, project: Project): List<KotlinScriptLibraryEntity> {
    val configurationManager = ScriptConfigurationManager.getInstance(project)

    val dependenciesClassFiles = ScriptDependencyAware.getInstance(project).getScriptDependenciesClassFiles(scriptFile)
    val dependenciesSourceFiles = configurationManager.getScriptDependenciesSourceFiles(scriptFile)

    // List builders are not supported by WorkspaceModel yet
    val libraries = mutableListOf<KotlinScriptLibraryEntity.Builder>()

    libraries.fillWithFiles(project, dependenciesClassFiles, dependenciesSourceFiles)
    libraries.fillWithIdeSpecificDependencies(project, scriptFile)

    val mergedLibraries = libraries.mergeClassAndSourceRoots()

    return mergedLibraries
        .map { library ->
            val existingLibrary = resolve(KotlinScriptLibraryId(library.name))
            if (existingLibrary != null) {
                if (!existingLibrary.hasSameRootsAs(library)) {
                    modifyKotlinScriptLibraryEntity(existingLibrary) {
                        roots = library.roots.toMutableList()
                    }
                }
                existingLibrary
            } else {
                addEntity(library)
            }
        }
}

private fun MutableList<KotlinScriptLibraryEntity.Builder>.mergeClassAndSourceRoots(): List<KotlinScriptLibraryEntity.Builder> {
    return groupBy { it.name }.values
        .map { libsWithSameName ->
            if (libsWithSameName.size == 1) libsWithSameName.single()
            else { // 2
                KotlinScriptLibraryEntity.Companion(
                    libsWithSameName.first().name,
                    libsWithSameName.flatMap { it.roots }, false,
                    libsWithSameName.first().usedInScripts,
                    libsWithSameName.first().entitySource
                )
            }
        }
}

private fun MutableList<KotlinScriptLibraryEntity.Builder>.fillWithIdeSpecificDependencies(project: Project, scriptFile: VirtualFile) {
    ScriptAdditionalIdeaDependenciesProvider.getRelatedLibraries(scriptFile, project).forEach { lib ->
        val provider = lib.rootProvider
        fillWithFiles(
            project,
            provider.getFiles(OrderRootType.CLASSES).asList(),
            provider.getFiles(OrderRootType.SOURCES).asList(),
        )
    }
}

private fun KotlinScriptLibraryEntity.hasSameRootsAs(dependency: KotlinScriptLibraryEntity.Builder): Boolean =
    this.roots.containsAll(dependency.roots) && dependency.roots.containsAll(this.roots)

fun VirtualFile.relativeName(project: Project): String =
    if (ScratchUtil.isScratch(this) || this is LightVirtualFile) presentableName
    else toNioPath().relativeToOrNull(Path.of(project.basePath!!))?.pathString
        ?: presentableName

private fun Project.createLibraryEntity(
    name: String,
    dependency: VirtualFile,
    rootTypeId: KotlinScriptLibraryRootTypeId,
): KotlinScriptLibraryEntity.Builder {

    val fileUrlManager = WorkspaceModel.getInstance(this).getVirtualFileUrlManager()
    val fileUrl = dependency.toVirtualFileUrl(fileUrlManager)
    val libraryEntitySource = KotlinScriptLibraryEntitySource(fileUrl)
    val libraryRoots = mutableListOf(KotlinScriptLibraryRoot(fileUrl, rootTypeId))

    return KotlinScriptLibraryEntity.Companion(name, libraryRoots, false, emptySet(), libraryEntitySource)
}