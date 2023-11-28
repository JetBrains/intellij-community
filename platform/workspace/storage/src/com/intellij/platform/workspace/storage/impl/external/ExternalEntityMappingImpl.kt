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
import java.util.*

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

  constructor() : this(PersistentBidirectionalMapImpl<EntityId, T>().builder(), IndexLog(mutableListOf()))

  override fun addMapping(entity: WorkspaceEntity, data: T) {
    add((entity as WorkspaceEntityBase).id, data)
    (entityStorage as MutableEntityStorageImpl).incModificationCount()
  }

  internal fun add(id: EntityId, data: T) {
    index.put(id, data)
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

  internal fun clearMapping() {
    index.clear()
    indexLogBunches.clear()
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
    other.indexLogBunches.chain.forEach { operation ->
      when (operation) {
        is IndexLogOperation.Changes -> {
          operation.changes.values.forEach { record ->
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
        }
        IndexLogOperation.Clear -> {
          clearMapping()
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

  internal sealed interface IndexLogOperation {
    data class Changes(val changes: LinkedHashMap<EntityId, IndexLogRecord>) : IndexLogOperation
    object Clear : IndexLogOperation
  }

  internal class IndexLog(
    val chain: MutableList<IndexLogOperation>,
  ) {
    fun add(id: EntityId, operation: IndexLogRecord) {
      var lastBunch = chain.lastOrNull()
      if (lastBunch !is IndexLogOperation.Changes) {
        chain.add(IndexLogOperation.Changes(LinkedHashMap()))
        lastBunch = chain.last()
      }
      lastBunch as IndexLogOperation.Changes
      val existing = lastBunch.changes[id]
      if (existing != null) {
        when (operation) {
          is IndexLogRecord.Add<*> -> {
            lastBunch.changes.remove(id)
            lastBunch.changes[id] = operation
          }
          is IndexLogRecord.Remove -> {
            if (existing is IndexLogRecord.Add<*>) {
              lastBunch.changes.remove(id)
            } else {
              lastBunch.changes[id] = operation
            }
          }
        }
      } else {
        lastBunch.changes[id] = operation
      }
    }

    fun clear() {
      chain.add(IndexLogOperation.Clear)
    }
  }

  companion object {
    fun fromMap(other: Map<String, ExternalEntityMappingImpl<*>>): MutableMap<String, MutableExternalEntityMappingImpl<*>> {
      val result = mutableMapOf<String, MutableExternalEntityMappingImpl<*>>()
      other.forEach { (identifier, index) ->
        if (index is MutableExternalEntityMappingImpl) error("Cannot create mutable index from mutable index")
        result[identifier] = MutableExternalEntityMappingImpl((index.index as PersistentBidirectionalMap.Immutable).builder(), IndexLog(mutableListOf()))
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
