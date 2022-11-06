// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.openapi.project.Project
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilders
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity

/**
 * See recommendations for custom entities indexing
 * [here](https://youtrack.jetbrains.com/articles/IDEA-A-239/Integration-of-custom-workspace-entities-with-platform-functionality)
 */
class KotlinScriptDependencyIndexableEntityProvider : IndexableEntityProvider.Existing<KotlinScriptEntity> {

    override fun getEntityClass(): Class<KotlinScriptEntity> = KotlinScriptEntity::class.java

    override fun getAddedEntityIteratorBuilders(
        entity: KotlinScriptEntity,
        project: Project
    ): Collection<IndexableEntityProvider.IndexableIteratorBuilder> = buildList {
        entity.dependencies.forEach {
            addAll(createIteratorBuildersForDependency(it))
        }
    }

    override fun getReplacedEntityIteratorBuilders(
        oldEntity: KotlinScriptEntity,
        newEntity: KotlinScriptEntity
    ): Collection<IndexableEntityProvider.IndexableIteratorBuilder> {
        val notYetIndexed = newEntity.dependencies.toSet() - oldEntity.dependencies.toSet()
        return buildList {
            notYetIndexed.forEach {
                addAll(createIteratorBuildersForDependency(it))
            }
        }
    }

    override fun getIteratorBuildersForExistingModule(
        entity: ModuleEntity,
        entityStorage: EntityStorage,
        project: Project
    ): Collection<IndexableEntityProvider.IndexableIteratorBuilder> = emptyList()


    private fun createIteratorBuildersForDependency(dependency: LibraryEntity): Collection<IndexableEntityProvider.IndexableIteratorBuilder> =
        IndexableIteratorBuilders.forLibraryEntity(dependency.symbolicId, true)
}