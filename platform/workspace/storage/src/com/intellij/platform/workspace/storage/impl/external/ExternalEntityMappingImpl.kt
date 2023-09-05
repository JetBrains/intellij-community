// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.impl.external

import com.google.common.collect.HashBiMap
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.workspace.storage.ExternalEntityMapping
import com.intellij.platform.workspace.storage.MutableExternalEntityMapping
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.*
import com.intellij.platform.workspace.storage.impl.containers.PersistentBidirectionalMap
import com.intellij.platform.workspace.storage.impl.containers.PersistentBidirectionalMapImpl
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import java.util.*

@OptIn(EntityStorageInstrumentationApi::class)
internal open class ExternalEntityMappingImpl<T> internal constructor(internal open val index: PersistentBidirectionalMap<EntityId, T>)
  : ExternalEntityMapping<T> {
  protected lateinit var entityStorage: AbstractEntityStorage

  override fun getEntities(data: T): List<WorkspaceEntity> {
    return index.getKeysByValue(data)?.mapNotNull {
      entityStorage.entityDataById(it)?.createEntity(entityStorage)
    } ?: emptyList()
  }

  override fun getFirstEntity(data: T): WorkspaceEntity? {
    return index.getKeysByValue(data)?.firstOrNull()?.let {
      entityStorage.entityDataById(it)?.createEntity(entityStorage)
    }
  }

  override fun getDataByEntity(entity: WorkspaceEntity): T? {
    entity as WorkspaceEntityBase
    return getDataByEntityId(entity.id)
  }

  internal fun getDataByEntityId(entityId: EntityId): T? {
    return index[entityId]
  }

  override fun size(): Int = index.size

  internal fun setTypedEntityStorage(storage: AbstractEntityStorage) {
    entityStorage = storage
  }

  override fun forEach(action: (key: WorkspaceEntity, value: T) -> Unit) {
    index.forEach { key, value -> action(entityStorage.entityDataByIdOrDie(key).createEntity(entityStorage), value) }
  }
}

internal class MutableExternalEntityMappingImpl<T> private constructor(
  override var index: PersistentBidirectionalMap.Builder<EntityId, T>,
  internal var indexLogBunches: IndexLog,
) : ExternalEntityMappingImpl<T>(index), MutableExternalEntityMapping<T> {

  constructor() : this(PersistentBidirectionalMapImpl<EntityId, T>().builder(), IndexLog(LinkedHashMap()))

  override fun addMapping(entity: WorkspaceEntity, data: T) {
    add((entity as WorkspaceEntityBase).id, data)
    (entityStorage as MutableEntityStorageImpl).incModificationCount()
  }

  internal fun add(id: EntityId, data: T) {
    val removedValue = index.put(id, data)
    if (removedValue != null) {
      indexLogBunches.add(id, IndexLogRecord.Remove(id))
    }
    indexLogBunches.add(id, IndexLogRecord.Add(id, data))
    LOG.trace {
      try {
        "Adding to external index: ${id.asString()} -> $data. Data hash: ${data.hashCode()}"
      }
      catch (e: Throwable) {
        "Adding to external index. ${id.asString()}, cannot get data info. ${e.message}"
      }
    }
  }

  override fun addIfAbsent(entity: WorkspaceEntity, data: T): Boolean {
    entity as WorkspaceEntityBase
    return if (entity.id !in index) {
      add(entity.id, data)
      true
    } else false
  }

  override fun getOrPutDataByEntity(entity: WorkspaceEntity, defaultValue: () -> T): T {
    return getDataByEntity(entity) ?: run {
      val defaultVal = defaultValue()
      add((entity as WorkspaceEntityBase).id, defaultVal)
      defaultVal
    }
  }

  override fun removeMapping(entity: WorkspaceEntity): T? {
    entity as WorkspaceEntityBase
    val removed = remove(entity.id)
    (entityStorage as MutableEntityStorageImpl).incModificationCount()
    return removed
  }

  internal fun remove(id: EntityId): T? {
    LOG.trace { "Remove $id from external index" }
    val removed = index.remove(id)
    if (removed != null) {
      indexLogBunches.add(id, IndexLogRecord.Remove(id))
    }
    return removed
  }

  fun applyChanges(other: MutableExternalEntityMappingImpl<*>,
                   replaceMap: HashBiMap<NotThisEntityId, ThisEntityId>,
                   target: MutableEntityStorageImpl) {
    other.indexLogBunches.changes.values.forEach { record ->
      applyChange(replaceMap, target, record.first)
      record.second?.let { applyChange(replaceMap, target, it) }
    }
  }

  private fun applyChange(replaceMap: HashBiMap<NotThisEntityId, ThisEntityId>,
                          target: MutableEntityStorageImpl,
                          record: IndexLogRecord) {
    when (record) {
      is IndexLogRecord.Add<*> -> {
        getTargetId(replaceMap, target, record.id)?.let { entityId ->
          @Suppress("UNCHECKED_CAST")
          add(entityId, record.data as T)
        }
      }
      is IndexLogRecord.Remove -> {
        getTargetId(replaceMap, target, record.id)?.let { entityId ->
          remove(entityId)
        }
      }
    }
  }

  private fun getTargetId(replaceMap: HashBiMap<NotThisEntityId, ThisEntityId>, target: MutableEntityStorageImpl, id: EntityId): EntityId? {
    val possibleTargetId = replaceMap[id.notThis()]
    if (possibleTargetId != null) return possibleTargetId.id

    if (target.entityDataById(id) == null) return null

    // It's possible that before addDiff there was a gup in this particular id. If it's so, replaceMap should not have a mapping to it
    val sourceId = replaceMap.inverse()[id.asThis()]
    return if (sourceId != null) null else id
  }

  private fun toImmutable(): ExternalEntityMappingImpl<T> {
    return ExternalEntityMappingImpl(this.index.build())
  }

  internal sealed class IndexLogRecord {
    data class Add<T>(val id: EntityId, val data: T) : IndexLogRecord()
    data class Remove(val id: EntityId) : IndexLogRecord()
  }

  internal class IndexLog(
    // Pair may have one of three allowed states:
    //  [Added, null], [Removed, null], [Removed, Added]
    // The last state is used when the value is replaced with the new one
    val changes: LinkedHashMap<EntityId, Pair<IndexLogRecord, IndexLogRecord?>>,
  ) {
    fun add(id: EntityId, operation: IndexLogRecord) {
      val existing = changes[id]
      if (existing != null) {
        when (operation) {
          is IndexLogRecord.Add<*> -> {
            val newValue = if (existing.second == null) {
              val firstValue = existing.first
              when(firstValue) {
                is IndexLogRecord.Add<*> -> existing.copy(first = operation)
                is IndexLogRecord.Remove -> existing.copy(second = operation)
              }
            }
            else {
              check(existing.second is IndexLogRecord.Add<*>)
              check(existing.first is IndexLogRecord.Remove)
              existing.copy(second = operation)
            }
            changes[id] = newValue
          }
          is IndexLogRecord.Remove -> {
            val newValue = if (existing.second == null) {
              val firstValue = existing.first
              when(firstValue) {
                is IndexLogRecord.Add<*> -> null
                is IndexLogRecord.Remove -> existing.copy(first = operation)
              }
            }
            else {
              val firstRemoval = existing.first
              check(firstRemoval is IndexLogRecord.Remove)
              existing.copy(second = null)
            }
            if (newValue != null) changes[id] = newValue else changes.remove(id)
          }
        }
      } else {
        changes[id] = operation to null
      }
    }
  }

  companion object {
    fun fromMap(other: Map<String, ExternalEntityMappingImpl<*>>): MutableMap<String, MutableExternalEntityMappingImpl<*>> {
      val result = mutableMapOf<String, MutableExternalEntityMappingImpl<*>>()
      other.forEach { (identifier, index) ->
        if (index is MutableExternalEntityMappingImpl) error("Cannot create mutable index from mutable index")
        result[identifier] = MutableExternalEntityMappingImpl((index.index as PersistentBidirectionalMap.Immutable).builder(), IndexLog(LinkedHashMap()))
      }
      return result
    }

    fun toImmutable(other: MutableMap<String, MutableExternalEntityMappingImpl<*>>): Map<String, ExternalEntityMappingImpl<*>> {
      val result = mutableMapOf<String, ExternalEntityMappingImpl<*>>()
      other.forEach { (identifier, index) ->
        result[identifier] = index.toImmutable()
      }
      return Collections.unmodifiableMap(result)
    }

    private val LOG = logger<MutableExternalEntityMappingImpl<*>>()
  }
}

internal object EmptyExternalEntityMapping : ExternalEntityMapping<Any> {
  override fun getEntities(data: Any): List<WorkspaceEntity> = emptyList()
  override fun getFirstEntity(data: Any): WorkspaceEntity? = null
  override fun getDataByEntity(entity: WorkspaceEntity): Any? = null
  override fun forEach(action: (key: WorkspaceEntity, value: Any) -> Unit) {}
  override fun size(): Int = 0
}
