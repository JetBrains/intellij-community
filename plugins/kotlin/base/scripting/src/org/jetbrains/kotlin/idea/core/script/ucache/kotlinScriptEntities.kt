// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.collaboration.async.CompletableFutureUtil
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.applyIf
import com.intellij.workspaceModel.ide.BuilderSnapshot
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.toVirtualFileUrl
import com.intellij.workspaceModel.ide.impl.virtualFile
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.kotlin.idea.core.script.ScriptConfigurationManager
import org.jetbrains.kotlin.idea.core.script.dependencies.ScriptAdditionalIdeaDependenciesProvider
import com.intellij.openapi.application.runWriteAction
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

/**
 * This file contains functions to work with [WorkspaceModel] in the context of [KotlinScriptEntity].
 * For technical details, see [this article](https://jetbrains.team/p/wm/documents/Development/a/Workspace-model-custom-entities-creation).
 */

fun KotlinScriptEntity.listDependencies(rootTypeId: LibraryRootTypeId? = null): List<VirtualFile> = dependencies.asSequence()
    .flatMap { it.roots }
    .applyIf(rootTypeId != null) { filter { it.type == rootTypeId } }
    .mapNotNull { it.url.virtualFile }
    .filter { it.isValid }
    .toList()

/**
 *  Warning! Function requires platform write-lock acquisition to complete.
 */
internal fun Project.syncScriptEntities(actualScriptFiles: Sequence<VirtualFile>) {
    var replaced: CompletableFuture<Boolean>
    do {
        val snapshot = WorkspaceModel.getInstance(this).getBuilderSnapshot()
        snapshot.syncScriptEntities(actualScriptFiles, this)
        replaced = snapshot.replaceModelWithSelf(this)
    } while (!replaced.get())
}

private fun BuilderSnapshot.syncScriptEntities(filesToAddOrUpdate: Sequence<VirtualFile>, project: Project) {
    val fileUrlManager = VirtualFileUrlManager.getInstance(project)
    val actualPaths = filesToAddOrUpdate.map { it.path }.toSet()

    builder.entities(KotlinScriptEntity::class.java)
        .filter { it.path !in actualPaths }
        .forEach { builder.removeEntity(it) /* dependencies are removed automatically */ }

    filesToAddOrUpdate.forEach { scriptFile ->
        // WorkspaceModel API trait: LibraryEntity needs to be created first and only then it can be referred to (from ScriptEntity).
        // ScriptEntity cannot be fully created in a single step.

        val scriptDependencies = builder.addOrUpdateScriptDependencies(scriptFile, project)
        val scriptEntity = builder.resolve(ScriptId(scriptFile.path))
        if (scriptEntity == null) {
            val scriptSource = KotlinScriptEntitySource(scriptFile.toVirtualFileUrl(fileUrlManager))
            builder.addEntity(KotlinScriptEntity(scriptFile.path, scriptSource) {
                this.dependencies = scriptDependencies
            })
        } else {
            val outdatedDependencies =
                builder.entitiesBySource { it == scriptEntity.entitySource }[scriptEntity.entitySource]
                    ?.get(LibraryEntity::class.java)
                    ?.let { it.toSet() - scriptDependencies.toSet() }

            outdatedDependencies?.forEach { builder.removeEntity(it) }

            builder.modifyEntity(scriptEntity) {
                this.dependencies = scriptDependencies
            }
        }
    }
}

private fun BuilderSnapshot.replaceModelWithSelf(project: Project): CompletableFuture<Boolean> = CompletableFuture.supplyAsync(
    { runWriteAction { WorkspaceModel.getInstance(project).replaceProjectModel(getStorageReplacement()) } },
    CompletableFutureUtil.getEDTExecutor(ModalityState.NON_MODAL)
)

private fun MutableEntityStorage.addOrUpdateScriptDependencies(scriptFile: VirtualFile, project: Project): List<LibraryEntity> {
    val configurationManager = ScriptConfigurationManager.getInstance(project)
    val fileUrlManager = VirtualFileUrlManager.getInstance(project)

    val entitySource = KotlinScriptEntitySource(scriptFile.toVirtualFileUrl(fileUrlManager))
    val scriptDependencies = mutableListOf<LibraryEntity>() // list builders are not supported by WorkspaceModel yet

    val dependenciesClassFiles = configurationManager.getScriptDependenciesClassFiles(scriptFile).toMutableSet()
    val dependenciesSourceFiles = configurationManager.getScriptDependenciesSourceFiles(scriptFile).toMutableSet()

    addIdeSpecificDependencies(project, scriptFile, dependenciesClassFiles, dependenciesSourceFiles)

    if (dependenciesClassFiles.isNotEmpty() || dependenciesSourceFiles.isNotEmpty()) {
        scriptDependencies.add(
            project.createLibrary(
                "Script: ${scriptFile.relativeName(project)}",
                dependenciesClassFiles,
                dependenciesSourceFiles,
                entitySource
            )
        )
    }

    val scriptSdk = configurationManager.getScriptSdk(scriptFile)
    val projectSdk = ProjectRootManager.getInstance(project).projectSdk

    if (scriptSdk?.homePath != projectSdk?.homePath) {
        val sdkClassFiles = configurationManager.getScriptSdkDependenciesClassFiles(scriptFile)
        val sdkSourceFiles = configurationManager.getScriptSdkDependenciesSourceFiles(scriptFile)

        if (sdkClassFiles.isNotEmpty() || sdkSourceFiles.isNotEmpty()) {
            scriptDependencies.add(
                project.createLibrary(
                    "Script: ${scriptFile.relativeName(project)}: sdk<${scriptSdk?.name}>",
                    sdkClassFiles,
                    sdkSourceFiles,
                    entitySource
                )
            )
        }
    }

    return scriptDependencies.map { dependency ->
        val existingEntity = entities(LibraryEntity::class.java).find { it.name == dependency.name }
        if (existingEntity != null) {
            modifyEntity(existingEntity) { roots = dependency.roots.toMutableList() }
            existingEntity
        } else {
            addEntity(dependency)
            dependency
        }
    }
}

fun VirtualFile.relativeName(project: Project): String {
    return if (ScratchUtil.isScratch(this)) presentableName
    else toNioPath().relativeTo(Path.of(project.basePath!!)).pathString
}

private fun addIdeSpecificDependencies(
    project: Project,
    scriptFile: VirtualFile,
    classDependencies: MutableSet<VirtualFile>,
    sourceDependencies: MutableSet<VirtualFile>
) {
    ScriptAdditionalIdeaDependenciesProvider.getRelatedLibraries(scriptFile, project).forEach { lib ->
        val provider = lib.rootProvider
        provider.getFiles(OrderRootType.CLASSES).forEach { classDependencies.add(it) }
        provider.getFiles(OrderRootType.SOURCES).forEach { sourceDependencies.add(it) }
    }
}

private fun Project.createLibrary(
    name: String,
    classFiles: Collection<VirtualFile>,
    sources: Collection<VirtualFile> = emptyList(),
    entitySource: KotlinScriptEntitySource
): LibraryEntity {

    val fileUrlManager = VirtualFileUrlManager.getInstance(this)

    val libraryRoots = mutableListOf<LibraryRoot>()
    classFiles.forEach {
        val fileUrl = it.toVirtualFileUrl(fileUrlManager)
        libraryRoots.add(LibraryRoot(fileUrl, LibraryRootTypeId.COMPILED))
    }

    sources.forEach {
        val fileUrl = it.toVirtualFileUrl(fileUrlManager)
        libraryRoots.add(LibraryRoot(fileUrl, LibraryRootTypeId.SOURCES))
    }

    return LibraryEntity(name, LibraryTableId.ProjectLibraryTableId, libraryRoots, entitySource)
}