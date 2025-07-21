// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k1.dependencies

import com.intellij.java.workspace.fileIndex.JvmPackageRootData
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.core.fileIndex.impl.ModuleOrLibrarySourceRootData
import org.jetbrains.kotlin.idea.core.script.shared.indexSourceRootsEagerly
import org.jetbrains.kotlin.idea.core.script.k1.ucache.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.k1.ucache.KotlinScriptLibraryRootTypeId

/**
 * See recommendations for custom entities indexing
 * [here](https://youtrack.jetbrains.com/articles/IDEA-A-239/Integration-of-custom-workspace-entities-with-platform-functionality)
 */

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