// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.util.containers.addIfNotNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.core.script.*
import org.jetbrains.kotlin.idea.core.script.ScriptClassPathUtil.Companion.findVirtualFile
import org.jetbrains.kotlin.idea.core.script.ScriptClassPathUtil.Companion.findVirtualFiles
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.dependenciesSources
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.jvm.impl.toClassPathOrEmpty

fun MutableEntityStorage.addDependencies(
    wrapper: ScriptCompilationConfigurationWrapper,
    source: KotlinScriptEntitySource,
    definition: ScriptDefinition,
    locationName: String,
    project: Project
): List<LibraryDependency> {
    val storage = this

    return buildList {
        val classes = wrapper.dependenciesClassPath.mapNotNull { findVirtualFile(it.path) }

        if (wrapper.isUberDependencyAllowed()) {
            val sources =
                wrapper.configuration?.get(ScriptCompilationConfiguration.ide.dependenciesSources).findVirtualFiles()

            addIfNotNull(storage.createUberDependency(
                locationName = locationName,
                classes = classes,
                sources = sources,
                source = source,
                project = project
            ))
        } else {
            val definitionLibrary = getOrCreateDefinitionDependency(definition = definition, project = project, entitySource = source)
            add(LibraryDependency(definitionLibrary.symbolicId, false, DependencyScope.COMPILE))
            val definitionLibraryRoots = definitionLibrary.roots.toSet()

            for (virtualFile in classes) {
                val root = virtualFile.compiledLibraryRoot(project)
                if (definitionLibraryRoots.contains(root)) continue

                add(getOrCreateLibrary(virtualFile.name, listOf(root), source))
            }
        }
    }
}

private fun MutableEntityStorage.createUberDependency(
    locationName: String,
    classes: List<VirtualFile>,
    sources: List<VirtualFile>,
    source: KotlinScriptEntitySource,
    project: Project
): LibraryDependency? {
    if (classes.isEmpty() && sources.isEmpty()) return null

    val classRoots = classes.map { it.compiledLibraryRoot(project) }
    val sourceRoots = sources.map { it.sourceLibraryRoot(project) }

    return createOrUpdateLibrary("$locationName dependencies", classRoots + sourceRoots, source)
}

private fun ScriptCompilationConfigurationWrapper.isUberDependencyAllowed(): Boolean {
    return dependenciesSources.size + dependenciesClassPath.size < 20
}

@ApiStatus.Internal
private fun MutableEntityStorage.getOrCreateDefinitionDependency(
    definition: ScriptDefinition, project: Project, entitySource: EntitySource
): LibraryEntity {
    val libraryId = LibraryId(".${definition.fileExtension} definition dependencies", LibraryTableId.ProjectLibraryTableId)
    val library = resolve(libraryId)
    if (library != null) return library

    val fileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()

    val classes = definition.compilationConfiguration[ScriptCompilationConfiguration.dependencies]
        .toClassPathOrEmpty()
        .mapNotNull { findVirtualFile(it.path) }
        .sortedBy { it.name }

    val sources = definition.compilationConfiguration[ScriptCompilationConfiguration.ide.dependenciesSources]
        .toClassPathOrEmpty()
        .mapNotNull { findVirtualFile(it.path) }
        .sortedBy { it.name }

    val classRoots = classes.map {
        LibraryRoot(it.toVirtualFileUrl(fileUrlManager), LibraryRootTypeId.COMPILED)
    }

    val sourceRoots = sources.map {
        LibraryRoot(it.toVirtualFileUrl(fileUrlManager), LibraryRootTypeId.SOURCES)
    }

    return addEntity(LibraryEntity(libraryId.name, libraryId.tableId, classRoots + sourceRoots, entitySource))
}