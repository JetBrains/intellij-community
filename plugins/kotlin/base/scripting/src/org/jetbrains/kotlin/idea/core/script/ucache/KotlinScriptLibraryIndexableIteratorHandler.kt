// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.ucache

import com.intellij.openapi.project.Project
import com.intellij.util.indexing.roots.IndexableEntityProvider
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.LibraryIndexableFilesIterator
import com.intellij.util.indexing.roots.builders.IndexableIteratorBuilderHandler
import com.intellij.util.indexing.roots.kind.LibraryOrigin
import com.intellij.workspaceModel.storage.EntityStorage

class KotlinScriptLibraryIndexableIteratorHandler : IndexableIteratorBuilderHandler {
    override fun accepts(builder: IndexableEntityProvider.IndexableIteratorBuilder): Boolean =
        builder is KotlinScriptLibraryIdIteratorBuilder

    override fun instantiate(
        builders: Collection<IndexableEntityProvider.IndexableIteratorBuilder>,
        project: Project,
        entityStorage: EntityStorage
    ): List<IndexableFilesIterator> {
        @Suppress("UNCHECKED_CAST")
        builders as Collection<KotlinScriptLibraryIdIteratorBuilder>
        val idsToIndex = mutableSetOf<KotlinScriptLibraryId>()
        builders.forEach { builder ->
            val libraryId = builder.libraryId
            idsToIndex.add(libraryId)
        }

        val result = mutableListOf<IndexableFilesIterator>()
        val ids = mutableSetOf<LibraryOrigin>()
        idsToIndex.forEach { id ->
            createLibraryIterator(id, entityStorage)?.also {
                if (ids.add(it.origin)) {
                    result.add(it)
                }
            }
        }
        return result
    }

}

private fun createLibraryIterator(
    libraryId: KotlinScriptLibraryId,
    entityStorage: EntityStorage
): LibraryIndexableFilesIterator? {

    return entityStorage.entities(KotlinScriptLibraryEntity::class.java)
        .find { it.symbolicId == libraryId }
        ?.let {
            KotlinScriptLibraryIndexableFilesIteratorImpl.createIterator(it)
        }
}