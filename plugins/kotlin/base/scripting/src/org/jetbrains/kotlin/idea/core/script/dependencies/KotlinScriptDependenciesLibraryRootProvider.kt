// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.dependencies

import com.intellij.java.workspaceModel.fileIndex.JvmPackageRootData
import com.intellij.openapi.roots.impl.CustomEntityProjectModelInfoProvider
import com.intellij.openapi.roots.impl.CustomEntityProjectModelInfoProvider.LibraryRoots
import com.intellij.openapi.util.registry.Registry
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleOrLibrarySourceRootData
import com.intellij.workspaceModel.ide.virtualFile
import com.intellij.workspaceModel.storage.EntityStorage
import org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryRootTypeId

/**
 * See recommendations for custom entities indexing
 * [here](https://youtrack.jetbrains.com/articles/IDEA-A-239/Integration-of-custom-workspace-entities-with-platform-functionality)
 */

fun indexSourceRootsEagerly() = Registry.`is`("kotlin.scripting.index.dependencies.sources", false)

class KotlinScriptProjectModelInfoProvider : CustomEntityProjectModelInfoProvider<KotlinScriptLibraryEntity> {
    override fun getEntityClass(): Class<KotlinScriptLibraryEntity> = KotlinScriptLibraryEntity::class.java

    override fun getLibraryRoots(
        entities: Sequence<KotlinScriptLibraryEntity>,
        entityStorage: EntityStorage
    ): Sequence<LibraryRoots<KotlinScriptLibraryEntity>> =
        if (useWorkspaceFileContributor()) { // see KotlinScriptDependenciesLibraryRootProvider
            emptySequence()
        } else {
            entities.map { libEntity ->
                val (classes, sources) = libEntity.roots.partition { it.type == KotlinScriptLibraryRootTypeId.COMPILED }
                val classFiles = classes.mapNotNull { it.url.virtualFile }
                val sourceFiles = sources.mapNotNull { it.url.virtualFile }
                val includeSources = indexSourceRootsEagerly() || libEntity.indexSourceRoots
                LibraryRoots(libEntity, if (includeSources) sourceFiles else emptyList(), classFiles, emptyList(), null)
            }
        }
}

fun useWorkspaceFileContributor() = Registry.`is`("kotlin.script.use.workspace.file.index.contributor.api")

class KotlinScriptWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<KotlinScriptLibraryEntity> {
    override val entityClass: Class<KotlinScriptLibraryEntity>
        get() = KotlinScriptLibraryEntity::class.java

    override fun registerFileSets(entity: KotlinScriptLibraryEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
        if (!useWorkspaceFileContributor()) return // see KotlinScriptDependenciesLibraryRootProvider
        val (classes, sources) = entity.roots.partition { it.type == KotlinScriptLibraryRootTypeId.COMPILED }
        classes.forEach {
            registrar.registerFileSet(it.url, WorkspaceFileKind.EXTERNAL, entity, RootData)
        }

        if (indexSourceRootsEagerly() || entity.indexSourceRoots) {
            sources.forEach {
                registrar.registerFileSet(it.url, WorkspaceFileKind.EXTERNAL_SOURCE, entity, RootSourceData)
            }
        }
    }

    private object RootData : JvmPackageRootData
    private object RootSourceData : JvmPackageRootData, ModuleOrLibrarySourceRootData
}

