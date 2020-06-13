// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.external

import com.google.common.collect.HashBiMap
import com.intellij.util.containers.BidirectionalMap
import com.intellij.workspaceModel.storage.ExternalEntityMapping
import com.intellij.workspaceModel.storage.MutableExternalEntityMapping
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.AbstractEntityStorage
import com.intellij.workspaceModel.storage.impl.EntityId
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import java.util.*

internal open class ExternalEntityMappingImpl<T> internal constructor(internal val index: BidirectionalMap<EntityId, T>)
  : ExternalEntityMapping<T> {
  protected lateinit var entityStorage: AbstractEntityStorage

  override fun getEntities(data: T): List<WorkspaceEntity> = index.getKeysByValue(data)?.mapNotNull {
    entityStorage.entityDataById(it)?.createEntity(entityStorage)
  } ?: emptyList()

  override fun getDataByEntity(entity: WorkspaceEntity): T? {
    entity as WorkspaceEntityBase
    return index[entity.id]
  }

  internal fun setTypedEntityStorage(storage: AbstractEntityStorage) {
    entityStorage = storage
  }

  internal fun copyIndex(): BidirectionalMap<EntityId, T> {
    val copy = BidirectionalMap<EntityId, T>()
    index.keys.forEach { key -> index[key]?.also { value -> copy[key] = value } }
    return copy
  }
}

internal class MutableExternalEntityMappingImpl<T> private constructor(
  index: BidirectionalMap<EntityId, T>,
  private val indexLog: MutableList<IndexLogRecord>
) : ExternalEntityMappingImpl<T>(index), MutableExternalEntityMapping<T> {
  constructor() : this(BidirectionalMap<EntityId, T>(), mutableListOf())

  override fun addMapping(entity: WorkspaceEntity, data: T) {
    add((entity as WorkspaceEntityBase).id, data)
    (entityStorage as WorkspaceEntityStorageBuilderImpl).incModificationCount()
  }

  private fun add(id: EntityId, data: T) {
    index[id] = data
    indexLog.add(IndexLogRecord.Add(id, data))
  }

  override fun removeMapping(entity: WorkspaceEntity) {
    entity as WorkspaceEntityBase
    remove(entity.id)
    (entityStorage as WorkspaceEntityStorageBuilderImpl).incModificationCount()
  }

  internal fun remove(id: EntityId) {
    index.remove(id)
    indexLog.add(IndexLogRecord.Remove(id))
  }

  fun applyChanges(other: MutableExternalEntityMappingImpl<*>, replaceMap: HashBiMap<EntityId, EntityId>) {
    other.indexLog.forEach {
      when (it) {
        is IndexLogRecord.Add<*> -> add(replaceMap.getOrDefault(it.id, it.id), it.data as T)
        is IndexLogRecord.Remove -> remove(replaceMap.getOrDefault(it.id, it.id))
      }
    }
  }

  private fun toImmutable(): ExternalEntityMappingImpl<T> = ExternalEntityMappingImpl(copyIndex())

  private sealed class IndexLogRecord {
    data class Add<T>(val id: EntityId, val data: T) : IndexLogRecord()
    data class Remove(val id: EntityId) : IndexLogRecord()
  }

  companion object {
    fun from(other: MutableExternalEntityMappingImpl<*>): MutableExternalEntityMappingImpl<*> =
      MutableExternalEntityMappingImpl(other.copyIndex(), other.indexLog.toMutableList())

    fun fromMap(other: Map<String, ExternalEntityMappingImpl<*>>): MutableMap<String, MutableExternalEntityMappingImpl<*>> {
      val result = mutableMapOf<String, MutableExternalEntityMappingImpl<*>>()
      other.forEach { (identifier, index) ->
        result[identifier] = MutableExternalEntityMappingImpl(index.copyIndex(), mutableListOf())
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
  }
}

object EmptyExternalEntityMapping : ExternalEntityMapping<Any> {
  override fun getEntities(data: Any): List<WorkspaceEntity> = emptyList()
  override fun getDataByEntity(entity: WorkspaceEntity): Any? = null
}