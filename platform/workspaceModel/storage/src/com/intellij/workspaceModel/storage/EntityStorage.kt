// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.EntityStorageSnapshotImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlIndex
import org.jetbrains.annotations.NonNls
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * A prototype of a storage system for the project model which stores data in typed entities. Entities are represented by interfaces
 * implementing WorkspaceEntity interface.
 */

/**
 * Add this annotation to the field to mark this fields as a key field for replaceBySource operation.
 * Entities will be compared based on these fields.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.TYPE)
annotation class EqualsBy

interface EntityStorage {
  fun <E : WorkspaceEntity> entities(entityClass: Class<E>): Sequence<E>
  fun <E : WorkspaceEntity> entitiesAmount(entityClass: Class<E>): Int
  fun <E : WorkspaceEntity, R : WorkspaceEntity> referrers(e: E, entityClass: KClass<R>, property: KProperty1<R, EntityReference<E>>): Sequence<R>
  fun <E : WorkspaceEntityWithSymbolicId, R : WorkspaceEntity> referrers(id: SymbolicEntityId<E>, entityClass: Class<R>): Sequence<R>
  fun <E : WorkspaceEntityWithSymbolicId> resolve(id: SymbolicEntityId<E>): E?
  operator fun <E : WorkspaceEntityWithSymbolicId> contains(id: SymbolicEntityId<E>): Boolean

  /**
   * Please select a name for your mapping in a form `<product_id>.<mapping_name>`.
   * E.g.:
   *  - intellij.modules.bridge
   *  - intellij.facets.bridge
   *  - rider.backend.id
   */
  fun <T> getExternalMapping(identifier: @NonNls String): ExternalEntityMapping<T>
  fun getVirtualFileUrlIndex(): VirtualFileUrlIndex
  fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Map<EntitySource, Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>>
  fun <E : WorkspaceEntity> createReference(e: E): EntityReference<E>
  fun toSnapshot(): EntityStorageSnapshot
}

/**
 * Read-only interface to a storage. Use [MutableEntityStorage] to modify it.
 */
interface EntityStorageSnapshot : EntityStorage {
  companion object {
    fun empty(): EntityStorageSnapshot = EntityStorageSnapshotImpl.EMPTY
  }
}

fun EntityStorage.toBuilder(): MutableEntityStorage {
  return MutableEntityStorage.from(this)
}

