// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.external

import com.intellij.util.containers.BidirectionalMap
import com.intellij.workspace.api.TypedEntity
import com.intellij.workspace.api.pstorage.AbstractPEntityStorage
import com.intellij.workspace.api.pstorage.PId
import com.intellij.workspace.api.pstorage.PTypedEntity
import com.intellij.workspace.api.pstorage.external.ExternalEntityIndex.MutableExternalEntityIndex.IndexLogRecord.Add
import com.intellij.workspace.api.pstorage.external.ExternalEntityIndex.MutableExternalEntityIndex.IndexLogRecord.Remove
import java.util.*

open class ExternalEntityIndex<T> private constructor(internal val index: BidirectionalMap<PId, T>) {
  private lateinit var entityStorage: AbstractPEntityStorage

  fun getEntities(data: T): List<TypedEntity> = index.getKeysByValue(data)?.toMutableList()?.mapNotNull {
    entityStorage.entityDataById(it)?.createEntity(entityStorage)
  } ?: emptyList()

  fun getDataByEntity(entity: TypedEntity): T? {
    entity as PTypedEntity
    return index[entity.id]
  }

  internal fun setTypedEntityStorage(storage: AbstractPEntityStorage) {
    entityStorage = storage
  }

  internal fun copyIndex(): BidirectionalMap<PId, T> {
    val copy = BidirectionalMap<PId, T>()
    index.keys.forEach { key -> index[key]?.also { value -> copy[key] = value } }
    return copy
  }

  class MutableExternalEntityIndex<T> private constructor(
    index: BidirectionalMap<PId, T>,
    private val indexLog: MutableList<IndexLogRecord>
  ) : ExternalEntityIndex<T>(index) {
    constructor() : this(BidirectionalMap<PId, T>(), mutableListOf())

    fun index(entity: TypedEntity, data: T) {
      entity as PTypedEntity
      index(entity.id, data)
    }

    private fun index(id: PId, data: T) {
      index[id] = data
      indexLog.add(Add(id, data))
    }

    fun remove(entity: TypedEntity) {
      entity as PTypedEntity
      remove(entity.id)
    }

    private fun remove(id: PId) {
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
      data class Add<T>(val id: PId, val data: T) : IndexLogRecord()
      data class Remove(val id: PId) : IndexLogRecord()
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