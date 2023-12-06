// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl

import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.instrumentation.Modification

// Just a wrapper for entity id in THIS store
@JvmInline
internal value class ThisEntityId(val id: EntityId)

// Just a wrapper for entity id in some other store
@JvmInline
internal value class NotThisEntityId(val id: EntityId)

internal fun EntityId.asThis(): ThisEntityId = ThisEntityId(this)
internal fun EntityId.notThis(): NotThisEntityId = NotThisEntityId(this)

internal fun currentStackTrace(depth: Int): String = Throwable().stackTrace.take(depth).joinToString(separator = "\n") { it.toString() }

internal fun loadClassByName(name: String, classLoader: ClassLoader): Class<*> {
  if (name.startsWith("[")) return Class.forName(name)
  return classLoader.loadClass(name)
}

/**
 * This function checks if we try to add an entity as a child to itself.
 * It can't verify a circular dependency, and it's performed via a third entity
 */
internal fun checkCircularDependency(connectionId: ConnectionId, childId: Int, parentId: Int, storage: AbstractEntityStorage) {
  if (connectionId.parentClass == connectionId.childClass && childId == parentId) {
    val parentEntityId = createEntityId(parentId, connectionId.parentClass)
    val entityData = storage.entityDataByIdOrDie(parentEntityId)
    val entityPresentation = entityData.symbolicId()?.toString() ?: entityData.toString()
    error("""Trying to make a circular dependency in entities by setting an entity as a child of itself.
          |Entity class: ${connectionId.parentClass.findWorkspaceEntity()}
          |Entity: $entityPresentation
        """.trimMargin())
  }
}

/**
 * This function checks if we try to add an entity as a child to itself.
 * It can't verify a circular dependency, and it's performed via a third entity
 */
internal fun checkCircularDependency(childId: EntityId, parentId: EntityId, storage: AbstractEntityStorage) {
  if (childId == parentId) {
    val entityData = storage.entityDataByIdOrDie(parentId)
    val entityPresentation = entityData.symbolicId()?.toString() ?: entityData.toString()
    error("""Trying to make a circular dependency in entities by setting an entity as a child of itself.
          |Entity class: ${parentId.clazz.findWorkspaceEntity()}
          |Entity: $entityPresentation
        """.trimMargin())
  }
}

internal fun WorkspaceEntity.asBase(): WorkspaceEntityBase = this as WorkspaceEntityBase

internal val EntityStorage.mutable: MutableEntityStorage
  get() = this as MutableEntityStorage

/**
 * Create replace events from the list of modifications for [connectionId]
 */
internal fun MutableEntityStorageImpl.createReplaceEventsForUpdates(updates: Collection<Modification>, connectionId: ConnectionId) {
  updates.forEach { update ->
    when (update) {
      is Modification.Add -> {
        changeLog.addReplaceEventForNewChild(update.parent, connectionId, update.child.asChild(), incModificationCounter = true)

        changeLog.addReplaceEventForNewParent(update.child, connectionId, update.parent.asParent(), false)
      }
      is Modification.Remove -> {
        changeLog.addReplaceEventForRemovedChild(update.parent, connectionId, update.child.asChild(), incModificationCounter = true)

        changeLog.addReplaceEventForRemovedParent(update.child, connectionId, update.parent.asParent(), false)
      }
    }
  }
}
