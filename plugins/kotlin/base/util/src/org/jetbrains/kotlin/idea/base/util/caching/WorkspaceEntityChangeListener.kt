// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.util.caching

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.findLibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.storage.EntityChange
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity

abstract class WorkspaceEntityChangeListener<Entity : WorkspaceEntity, Value : Any>(
    protected val project: Project,
    private val afterChangeApplied: Boolean = true
) : WorkspaceModelChangeListener {
    protected abstract val entityClass: Class<Entity>

    protected abstract fun map(storage: EntityStorage, entity: Entity): Value?

    protected abstract fun entitiesChanged(outdated: List<Value>)

    final override fun beforeChanged(event: VersionedStorageChange) {
        if (!afterChangeApplied) {
            handleEvent(event)
        }
    }

    final override fun changed(event: VersionedStorageChange) {
        if (afterChangeApplied) {
            handleEvent(event)
        }
    }

    private fun handleEvent(event: VersionedStorageChange) {
        val storageBefore = event.storageBefore
        val changes = event.getChanges(entityClass).ifEmpty { return }

        val outdatedEntities: List<Value> = changes.asSequence()
            .mapNotNull(EntityChange<Entity>::oldEntity)
            .mapNotNull { map(storageBefore, it) }
            .toList()

        if (outdatedEntities.isNotEmpty()) {
            entitiesChanged(outdatedEntities)
        }
    }
}

abstract class ModuleEntityChangeListener(project: Project, afterChangeApplied: Boolean = true) :
    WorkspaceEntityChangeListener<ModuleEntity, Module>(project, afterChangeApplied) {
    override val entityClass: Class<ModuleEntity>
        get() = ModuleEntity::class.java

    override fun map(storage: EntityStorage, entity: ModuleEntity): Module? =
        entity.findModule(storage)
}

abstract class LibraryEntityChangeListener(project: Project, afterChangeApplied: Boolean = true) :
    WorkspaceEntityChangeListener<LibraryEntity, Library>(project, afterChangeApplied) {
    override val entityClass: Class<LibraryEntity>
        get() = LibraryEntity::class.java

    override fun map(storage: EntityStorage, entity: LibraryEntity): Library? =
        entity.findLibraryBridge(storage)
}