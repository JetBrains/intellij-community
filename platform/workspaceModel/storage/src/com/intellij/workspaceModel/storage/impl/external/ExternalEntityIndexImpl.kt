// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.external

import com.intellij.util.containers.BidirectionalMap
import com.intellij.workspaceModel.storage.ExternalEntityIndex
import com.intellij.workspaceModel.storage.MutableExternalEntityIndex
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.AbstractEntityStorage
import com.intellij.workspaceModel.storage.impl.EntityId
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.external.ExternalEntityIndexImpl.MutableExternalEntityIndexImpl.IndexLogRecord.Add
import com.intellij.workspaceModel.storage.impl.external.ExternalEntityIndexImpl.MutableExternalEntityIndexImpl.IndexLogRecord.Remove
import java.util.*

open class ExternalEntityIndexImpl<T> private constructor(internal val index: BidirectionalMap<EntityId, T>)
  : ExternalEntityIndex<T> {
  private lateinit var entityStorage: AbstractEntityStorage

  override fun getEntities(data: T): List<WorkspaceEntity> = index.getKeysByValue(data)?.toMutableList()?.mapNotNull {
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

  class MutableExternalEntityIndexImpl<T> private constructor(
    index: BidirectionalMap<EntityId, T>,
    private val indexLog: MutableList<IndexLogRecord>
  ) : ExternalEntityIndexImpl<T>(index), MutableExternalEntityIndex<T> {
    constructor() : this(BidirectionalMap<EntityId, T>(), mutableListOf())

    override fun index(entity: WorkspaceEntity, data: T) {
      entity as WorkspaceEntityBase
      index(entity.id, data)
    }

    private fun index(id: EntityId, data: T) {
      index[id] = data
      indexLog.add(Add(id, data))
    }

    override fun remove(entity: WorkspaceEntity) {
      entity as WorkspaceEntityBase
      remove(entity.id)
    }

    private fun remove(id: EntityId) {
      index.remove(id)
      indexLog.add(Remove(id))
    }

    fun applyChanges(other: MutableExternalEntityIndexImpl<*>) {
      other.indexLog.forEach {
        when (it) {
          is Add<*> -> index(it.id, it.data as T)
          is Remove -> remove(it.id)
        }
      }
    }

    private fun toImmutable(): ExternalEntityIndexImpl<T> = ExternalEntityIndexImpl(copyIndex())

    private sealed class IndexLogRecord {
      data class Add<T>(val id: EntityId, val data: T) : IndexLogRecord()
      data class Remove(val id: EntityId) : IndexLogRecord()
    }

    companion object {
      fun from(other: MutableExternalEntityIndexImpl<*>): MutableExternalEntityIndexImpl<*> =
        MutableExternalEntityIndexImpl(other.copyIndex(), other.indexLog.toMutableList())

      fun fromMap(other: Map<String, ExternalEntityIndexImpl<*>>): MutableMap<String, MutableExternalEntityIndexImpl<*>> {
        val result = mutableMapOf<String, MutableExternalEntityIndexImpl<*>>()
        other.forEach { (identifier, index) ->
          result[identifier] = MutableExternalEntityIndexImpl(index.copyIndex(), mutableListOf())
        }
        return result
      }

      fun fromMutableMap(other: MutableMap<String, MutableExternalEntityIndexImpl<*>>): MutableMap<String, MutableExternalEntityIndexImpl<*>> {
        val result = mutableMapOf<String, MutableExternalEntityIndexImpl<*>>()
        other.forEach { (identifier, index) ->
          result[identifier] = MutableExternalEntityIndexImpl(index.copyIndex(), index.indexLog.toMutableList())
        }
        return result
      }

      fun toImmutable(other: MutableMap<String, MutableExternalEntityIndexImpl<*>>): Map<String, ExternalEntityIndexImpl<*>> {
        val result = mutableMapOf<String, ExternalEntityIndexImpl<*>>()
        other.forEach { (identifier, index) ->
          result[identifier] = index.toImmutable()
        }
        return Collections.unmodifiableMap(result)
      }
    }
  }
}