// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.BuilderSnapshot
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.applyIf
import com.intellij.workspaceModel.ide.getInstance
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.dependencies.ScriptAdditionalIdeaDependenciesProvider
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
    val storage = WorkspaceModel.getInstance(project).currentSnapshot
    val index = storage.getVirtualFileUrlIndex()
    val fileUrlManager = VirtualFileUrlManager.getInstance(project)

    var currentFile: VirtualFile? = this
    while (currentFile != null) {
        val entities = index.findEntitiesByUrl(fileUrlManager.getOrCreateFromUri(currentFile.url))
        if (entities.none()) {
            currentFile = currentFile.parent
            continue
        }

        return entities
            .mapNotNull { it.first as? KotlinScriptLibraryEntity }
            .flatMap { it.usedInScripts }
            .map { storage.resolve(it) ?: error("Unresolvable script: ${it.path}") }
            .toList()
    }
    return null
}

internal fun BuilderSnapshot.syncScriptEntities(
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

@Suppress("unused")
// Use this method troubleshooting to see scripts-to-libraries relations
private fun Project.scriptsDebugInfo(): String {
    val storage = WorkspaceModel.getInstance(this).currentSnapshot
    val scriptEntities = storage.entities(KotlinScriptEntity::class.java).toList()

    return buildString {
        append("Total number of scripts: ${scriptEntities.size}\n\n")
        append(
            scriptEntities.asSequence()
                .map { it.debugInfo(storage) }
                .joinToString("\n\n")
        )
    }
}

@Suppress("unused")
// Use this method troubleshooting to see libraries-to-scripts relations
private fun Project.scriptLibrariesDebugInfo(): String {
    val storage = WorkspaceModel.getInstance(this).currentSnapshot
    val scriptLibraries = storage.entities(KotlinScriptLibraryEntity::class.java).toList()

    return buildString {
        append("Total number of libraries: ${scriptLibraries.size}\n\n")
        append(
            scriptLibraries.asSequence()
                .map { it.debugInfo(storage) }
                .joinToString("\n\n")
        )
    }
}

private fun KotlinScriptLibraryEntity.debugInfo(storage: EntityStorage): String {
    return buildString {
        append("[$name, rootsNum=${roots.size}, usedInScriptsNum=${usedInScripts.size}]")
        append("\n")
        append(usedInScripts.joinToString("\n") {
            val scriptEntity = storage.resolve(it)
            "   - ${it.path}, libsNum=${scriptEntity?.dependencies?.size}"
        })
    }
}


private fun KotlinScriptEntity.debugInfo(storage: EntityStorage): String {
    return buildString {
        append("[$path, libsNum=${dependencies.size}]")
        append("\n")
        append(dependencies.joinToString("\n") {
            val libraryEntity = storage.resolve(it)
            "   - ${it.name}, rootsNum=${libraryEntity?.roots?.size}, reusedNum=${libraryEntity?.usedInScripts?.size}"
        })
    }
}

@Suppress("unused") // exists for debug purposes
private fun managerScriptsDebugInfo(project: Project, scriptFiles: Sequence<VirtualFile>? = null): String = buildString {
    val configurationManager = ScriptConfigurationManager.getInstance(project)

    val allSourcesSize = configurationManager.getAllScriptDependenciesSources().size
    val allSdkSourcesSize = configurationManager.getAllScriptSdkDependenciesSources().size

    val allClassesSize = configurationManager.getAllScriptsDependenciesClassFiles().size
    val allSdkClassesSize = configurationManager.getAllScriptsSdkDependenciesClassFiles().size

    scriptFiles?.forEach {
        val classDepSize = configurationManager.getScriptDependenciesClassFiles(it).size
        val sourceDepSize = configurationManager.getScriptDependenciesSourceFiles(it).size
        append("[${it.path}]: classes: ${classDepSize}, sources: ${sourceDepSize}\n")
    }
    insert(
        0,
        "==> ScriptConfigurationManager (classes: $allClassesSize, sdkClasses: $allSdkClassesSize, sources: $allSourcesSize, sdkSources: $allSdkSourcesSize)\n"
    )
}

@Suppress("unused") // exists for debug purposes
private fun scriptEntitiesDebugInfo(project: Project, listRoots: Boolean = false): String {
    fun List<KotlinScriptLibraryRoot>.print(indent: CharSequence = "          ") = asSequence()
        .mapIndexed { i, root -> "$indent${i + 1}: ${root.url.presentableUrl}" }
        .joinToString("\n", indent)

    return buildString {
        val entityStorage = WorkspaceModel.getInstance(project).currentSnapshot

        val allClasses = HashSet<KotlinScriptLibraryRoot>()
        val allSources = HashSet<KotlinScriptLibraryRoot>()

        entityStorage.entities(KotlinScriptEntity::class.java).forEachIndexed { scriptIndex, scriptEntity ->
            append("#${scriptIndex + 1}: [${scriptEntity.path}]\n")
            scriptEntity.dependencies.forEachIndexed dependencies@{ libIndex, libId ->
                val lib = entityStorage.resolve(libId) ?: return@dependencies

                val (classes, sources) = lib.roots.partition { it.type == KotlinScriptLibraryRootTypeId.COMPILED }
                allClasses.addAll(classes)
                allSources.addAll(sources)
                append("      Lib #${libIndex + 1}: \"${lib.name}\", classes: ${classes.size}, sources: ${sources.size} \n")
                applyIf(listRoots) {
                    append("        Classes:\n ${classes.print()}\n")
                    append("        Sources:\n ${sources.print()}\n")
                }
            }
        }

        insert(0, "==> WorkspaceModel (unique classes: ${allClasses.size}, sources: ${allSources.size})\n")
    }
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
            modifyEntity(scriptEntity) {
                dependencies.removeAll(refsToRemove)
                dependencies.addAll(refsToAdd)
            }
        }

        actualLibraries.forEach {
            if (!it.usedInScripts.contains(scriptEntity.symbolicId)) {
                modifyEntity(it) {
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
            modifyEntity(it) {
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
    val fileUrlManager = VirtualFileUrlManager.getInstance(project)
    val scriptSource = KotlinScriptEntitySource(scriptFile.toVirtualFileUrl(fileUrlManager))
    val scriptEntity = KotlinScriptEntity(scriptFile.path, libraryRefs, scriptSource)
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
                        modifyEntity(it) {
                            usedInScripts.remove(outdatedScript.symbolicId)
                        }
                    }
                }
            removeEntity(outdatedScript)
        }
}

private fun MutableList<KotlinScriptLibraryEntity>.fillWithFiles(
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

    val dependenciesClassFiles = configurationManager.getScriptDependenciesClassFiles(scriptFile)
    val dependenciesSourceFiles = configurationManager.getScriptDependenciesSourceFiles(scriptFile)

    // List builders are not supported by WorkspaceModel yet
    val libraries = mutableListOf<KotlinScriptLibraryEntity>()

    libraries.fillWithFiles(project, dependenciesClassFiles, dependenciesSourceFiles)
    libraries.fillWithIdeSpecificDependencies(project, scriptFile)

    val mergedLibraries = libraries.mergeClassAndSourceRoots()

    return mergedLibraries
        .map { library ->
            val existingLibrary = resolve(library.symbolicId)
            if (existingLibrary != null) {
                if (!existingLibrary.hasSameRootsAs(library)) {
                    modifyEntity(existingLibrary) {
                        roots = library.roots.toMutableList()
                    }
                }
                existingLibrary
            } else {
                addEntity(library)
            }
        }
}

private fun MutableList<KotlinScriptLibraryEntity>.mergeClassAndSourceRoots() =
    groupBy { it.name }.values
        .map { libsWithSameName ->
            if (libsWithSameName.size == 1) libsWithSameName.single()
            else { // 2
                KotlinScriptLibraryEntity(
                    libsWithSameName.first().name,
                    libsWithSameName.flatMap { it.roots }, false,
                    libsWithSameName.first().usedInScripts,
                    libsWithSameName.first().entitySource
                )
            }
        }

private fun MutableList<KotlinScriptLibraryEntity>.fillWithIdeSpecificDependencies(project: Project, scriptFile: VirtualFile) {
    ScriptAdditionalIdeaDependenciesProvider.getRelatedLibraries(scriptFile, project).forEach { lib ->
        val provider = lib.rootProvider
        fillWithFiles(
            project,
            provider.getFiles(OrderRootType.CLASSES).asList(),
            provider.getFiles(OrderRootType.SOURCES).asList(),
        )
    }
}

private fun KotlinScriptLibraryEntity.hasSameRootsAs(dependency: KotlinScriptLibraryEntity): Boolean =
    this.roots.containsAll(dependency.roots) && dependency.roots.containsAll(this.roots)

internal fun VirtualFile.relativeName(project: Project): String =
    if (ScratchUtil.isScratch(this) || this is LightVirtualFile) presentableName
    else toNioPath().relativeToOrNull(Path.of(project.basePath!!))?.pathString
        ?: presentableName

private fun Project.createLibraryEntity(
    name: String,
    dependency: VirtualFile,
    rootTypeId: KotlinScriptLibraryRootTypeId,
): KotlinScriptLibraryEntity {

    val fileUrlManager = VirtualFileUrlManager.getInstance(this)
    val fileUrl = dependency.toVirtualFileUrl(fileUrlManager)
    val libraryEntitySource = KotlinScriptLibraryEntitySource(fileUrl)
    val libraryRoots = mutableListOf(KotlinScriptLibraryRoot(fileUrl, rootTypeId))

    return KotlinScriptLibraryEntity(name, libraryRoots, false, emptySet(), libraryEntitySource)
}