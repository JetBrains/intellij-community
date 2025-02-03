// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.scripting

import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import org.jetbrains.kotlin.idea.core.script.dependencies.indexSourceRootsEagerly
import org.jetbrains.kotlin.idea.gradleJava.scripting.GradleScriptConfigurationsSource.KotlinGradleScriptModuleEntitySource

class GradleKotlinScriptIndexContributor : WorkspaceFileIndexContributor<LibraryEntity> {
    override val entityClass: Class<LibraryEntity>

    get() = LibraryEntity::class.java

    override fun registerFileSets(
        entity: LibraryEntity,
        registrar: WorkspaceFileSetRegistrar,
        storage: EntityStorage
    ) {
        val sources = entity.roots.filter { it.type == LibraryRootTypeId.SOURCES }
        if (!indexSourceRootsEagerly() && KotlinGradleScriptModuleEntitySource::class.java.isAssignableFrom(entity.entitySource::class.java)) {
            sources.forEach {
                registrar.registerExcludedRoot(it.url, WorkspaceFileKind.EXTERNAL_SOURCE, entity)
            }
        }
    }
}

