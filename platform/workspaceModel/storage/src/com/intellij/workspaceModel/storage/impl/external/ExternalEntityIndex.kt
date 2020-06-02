// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.external

import com.intellij.util.containers.BidirectionalMap
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.AbstractEntityStorage
import com.intellij.workspaceModel.storage.impl.EntityId
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.external.ExternalEntityIndex.MutableExternalEntityIndex.IndexLogRecord.Add
import com.intellij.workspaceModel.storage.impl.external.ExternalEntityIndex.MutableExternalEntityIndex.IndexLogRecord.Remove
import java.util.*

open class ExternalEntityIndex<T> private constructor(internal val index: BidirectionalMap<EntityId, T>) {
  private lateinit var entityStorage: AbstractEntityStorage

  fun getEntities(data: T): List<WorkspaceEntity> = index.getKeysByValue(data)?.toMutableList()?.mapNotNull {
    entityStorage.entityDataById(it)?.createEntity(entityStorage)
  } ?: emptyList()

  fun getDataByEntity(entity: WorkspaceEntity): T? {
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

  class MutableExternalEntityIndex<T> private constructor(
    index: BidirectionalMap<EntityId, T>,
    private val indexLog: MutableList<IndexLogRecord>
  ) : ExternalEntityIndex<T>(index) {
    constructor() : this(BidirectionalMap<EntityId, T>(), mutableListOf())

    fun index(entity: WorkspaceEntity, data: T) {
      entity as WorkspaceEntityBase
      index(entity.id, data)
    }

    private fun index(id: EntityId, data: T) {
      index[id] = data
      indexLog.add(Add(id, data))
    }

    fun remove(entity: WorkspaceEntity) {
      entity as WorkspaceEntityBase
      remove(entity.id)
    }

    private fun remove(id: EntityId) {
      index.remove(id)
      indexLog.add(Remove(id))
    }

    fun applyChanges(other: MutableExternalEntityIndex<*>) {
      other.indexLog.forEach {
        when (it) {
          is Add<*> -> index(it.id, it.data as T)
          is Remove -> remove(it.id)
        }
      }
    }

    private fun toImmutable(): ExternalEntityIndex<T> = ExternalEntityIndex(copyIndex())

    private sealed class IndexLogRecord {
      data class Add<T>(val id: EntityId, val data: T) : IndexLogRecord()
      data class Remove(val id: EntityId) : IndexLogRecord()
    }

    companion object {
      fun from(other: MutableExternalEntityIndex<*>): MutableExternalEntityIndex<*> =
        MutableExternalEntityIndex(other.copyIndex(), other.indexLog.toMutableList())

      fun fromMap(other: Map<String, ExternalEntityIndex<*>>): MutableMap<String, MutableExternalEntityIndex<*>> {
        val result = mutableMapOf<String, MutableExternalEntityIndex<*>>()
        other.forEach { (identifier, index) ->
          result[identifier] = MutableExternalEntityIndex(index.copyIndex(), mutableListOf())
        }
        return result
      }

      fun fromMutableMap(other: MutableMap<String, MutableExternalEntityIndex<*>>): MutableMap<String, MutableExternalEntityIndex<*>> {
        val result = mutableMapOf<String, MutableExternalEntityIndex<*>>()
        other.forEach { (identifier, index) ->
          result[identifier] = MutableExternalEntityIndex(index.copyIndex(), index.indexLog.toMutableList())
        }
        return result
      }

      fun toImmutable(other: MutableMap<String, MutableExternalEntityIndex<*>>): Map<String, ExternalEntityIndex<*>> {
        val result = mutableMapOf<String, ExternalEntityIndex<*>>()
        other.forEach { (identifier, index) ->
          result[identifier] = index.toImmutable()
        }
        return Collections.unmodifiableMap(result)
      }
    }
  }
}