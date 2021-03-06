// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.url.MutableVirtualFileUrlIndex
import com.intellij.workspaceModel.storage.url.VirtualFileUrlIndex
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

class StorageReplacement internal constructor(
  val version: Long,
  val snapshot: WorkspaceEntityStorage,
  val changes: Map<Class<*>, List<EntityChange<*>>>
)

class BuilderSnapshot(val version: Long, private val storage: WorkspaceEntityStorage) : WorkspaceEntityStorageBuilder {
  val builder: WorkspaceEntityStorageBuilder = WorkspaceEntityStorageBuilder.from(storage)

  /**
   * It's suggested to call this method WITHOUT write locks or anything
   */
  fun getStorageReplacement(): StorageReplacement {
    val changes = builder.collectChanges(storage)
    val newStorage = builder.toStorage()
    return StorageReplacement(version, newStorage, changes)
  }

  override fun <M : ModifiableWorkspaceEntity<T>, T : WorkspaceEntity> addEntity(clazz: Class<M>,
                                                                                 source: EntitySource,
                                                                                 initializer: M.() -> Unit): T {
    return builder.addEntity(clazz, source, initializer)
  }

  override fun <M : ModifiableWorkspaceEntity<out T>, T : WorkspaceEntity> modifyEntity(clazz: Class<M>, e: T, change: M.() -> Unit): T {
    return builder.modifyEntity(clazz, e, change)
  }

  override fun <T : WorkspaceEntity> changeSource(e: T, newSource: EntitySource): T {
    return builder.changeSource(e, newSource)
  }

  override fun removeEntity(e: WorkspaceEntity) {
    builder.removeEntity(e)
  }

  override fun replaceBySource(sourceFilter: (EntitySource) -> Boolean, replaceWith: WorkspaceEntityStorage) {
    builder.replaceBySource(sourceFilter, replaceWith)
  }

  override fun collectChanges(original: WorkspaceEntityStorage): Map<Class<*>, List<EntityChange<*>>> {
    return builder.collectChanges(original)
  }

  override fun toStorage(): WorkspaceEntityStorage {
    return builder.toStorage()
  }

  override fun <E : WorkspaceEntity> entities(entityClass: Class<E>): Sequence<E> {
    return builder.entities(entityClass)
  }

  override fun <E : WorkspaceEntity, R : WorkspaceEntity> referrers(e: E,
                                                                    entityClass: KClass<R>,
                                                                    property: KProperty1<R, EntityReference<E>>): Sequence<R> {
    return builder.referrers(e, entityClass, property)
  }

  override fun <E : WorkspaceEntityWithPersistentId, R : WorkspaceEntity> referrers(id: PersistentEntityId<E>,
                                                                                    entityClass: Class<R>): Sequence<R> {
    return builder.referrers(id, entityClass)
  }

  override fun <E : WorkspaceEntityWithPersistentId> resolve(id: PersistentEntityId<E>): E? {
    return builder.resolve(id)
  }

  override fun <T> getExternalMapping(identifier: String): ExternalEntityMapping<T> {
    return builder.getExternalMapping(identifier)
  }

  override fun getVirtualFileUrlIndex(): VirtualFileUrlIndex {
    return builder.getVirtualFileUrlIndex()
  }

  override fun entitiesBySource(sourceFilter: (EntitySource) -> Boolean): Map<EntitySource, Map<Class<out WorkspaceEntity>, List<WorkspaceEntity>>> {
    return builder.entitiesBySource(sourceFilter)
  }

  override fun <E : WorkspaceEntity> createReference(e: E): EntityReference<E> {
    return builder.createReference(e)
  }

  override fun isEmpty(): Boolean {
    return builder.isEmpty()
  }

  override fun addDiff(diff: WorkspaceEntityStorageDiffBuilder) {
    builder.addDiff(diff)
  }

  override fun <T> getMutableExternalMapping(identifier: String): MutableExternalEntityMapping<T> {
    return builder.getMutableExternalMapping(identifier)
  }

  override fun getMutableVirtualFileUrlIndex(): MutableVirtualFileUrlIndex {
    return builder.getMutableVirtualFileUrlIndex()
  }

  override val modificationCount: Long
    get() = builder.modificationCount
}
