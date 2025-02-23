// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.dependencies

import com.intellij.java.workspace.fileIndex.JvmPackageRootData
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleOrLibrarySourceRootData
import org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.ucache.KotlinScriptLibraryRootTypeId

/**
 * See recommendations for custom entities indexing
 * [here](https://youtrack.jetbrains.com/articles/IDEA-A-239/Integration-of-custom-workspace-entities-with-platform-functionality)
 */

fun indexSourceRootsEagerly(): Boolean = Registry.`is`("kotlin.scripting.index.dependencies.sources", false)

class KotlinScriptWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<KotlinScriptLibraryEntity> {
    override val entityClass: Class<KotlinScriptLibraryEntity>
        get() = KotlinScriptLibraryEntity::class.java

    override fun registerFileSets(entity: KotlinScriptLibraryEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
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