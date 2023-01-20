// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.openapi.project.Project
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.IndexableEntityProvider.IndexableIteratorBuilder
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import org.jetbrains.kotlin.idea.core.script.dependencies.useWorkspaceFileContributor

/**
 * See recommendations for custom entities indexing
 * [here](https://youtrack.jetbrains.com/articles/IDEA-A-239/Integration-of-custom-workspace-entities-with-platform-functionality)
 */
class KotlinScriptDependencyIndexableEntityProvider : IndexableEntityProvider.Existing<KotlinScriptEntity> {

    override fun getEntityClass(): Class<KotlinScriptEntity> = KotlinScriptEntity::class.java

    override fun getAddedEntityIteratorBuilders(entity: KotlinScriptEntity, project: Project): Collection<IndexableIteratorBuilder> =
        if (useWorkspaceFileContributor()) {
            emptyList()
        } else {
            buildList {
                fillWithLibsDiff(project, entity)
            }
        }

    override fun getReplacedEntityIteratorBuilders(
        oldEntity: KotlinScriptEntity,
        newEntity: KotlinScriptEntity,
        project: Project
    ): Collection<IndexableIteratorBuilder> =
        if (useWorkspaceFileContributor()) {
            emptyList()
        } else {
            buildList {
                fillWithLibsDiff(project, newEntity, oldEntity)
            }
        }

    override fun getIteratorBuildersForExistingModule(
        entity: ModuleEntity,
        entityStorage: EntityStorage,
        project: Project
    ): Collection<IndexableIteratorBuilder> = emptyList()
}

internal data class KotlinScriptLibraryIdIteratorBuilder(val libraryId: KotlinScriptLibraryId) :
    IndexableIteratorBuilder

private fun MutableList<IndexableIteratorBuilder>.fillWithLibsDiff(
    project: Project,
    newEntity: KotlinScriptEntity,
    oldEntity: KotlinScriptEntity? = null
) {
    val storage = WorkspaceModel.getInstance(project).currentSnapshot

    val notYetIndexed = if (oldEntity == null) {
        newEntity.dependencies.toSet()
    } else {
        newEntity.dependencies.toSet() - oldEntity.dependencies.toSet()
    }

    notYetIndexed.forEach { depId ->
        storage.resolve(depId)?.let {
            addAll(createIteratorBuildersForDependency(it))
        }
    }
}


private fun createIteratorBuildersForDependency(dependency: KotlinScriptLibraryEntity): Collection<IndexableIteratorBuilder> =
    forLibraryEntity(dependency.symbolicId)

private fun forLibraryEntity(libraryId: KotlinScriptLibraryId): Collection<IndexableIteratorBuilder> =
    listOf(KotlinScriptLibraryIdIteratorBuilder(libraryId))
