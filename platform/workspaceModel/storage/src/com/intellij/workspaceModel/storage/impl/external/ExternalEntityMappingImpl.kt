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
import com.intellij.workspaceModel.storage.impl.containers.copy
import org.jetbrains.annotations.TestOnly
import java.util.*

internal open class ExternalEntityMappingImpl<T> internal constructor(internal open val index: BidirectionalMap<EntityId, T>)
  : ExternalEntityMapping<T> {
  protected lateinit var entityStorage: AbstractEntityStorage

  override fun getEntities(data: T): List<WorkspaceEntity> = index.getKeysByValue(data)?.mapNotNull {
    entityStorage.entityDataById(it)?.createEntity(entityStorage)
  } ?: emptyList()

  override fun getDataByEntity(entity: WorkspaceEntity): T? {
    entity as WorkspaceEntityBase
    return index[entity.id]
  }

  @TestOnly
  fun size(): Int = index.size

  internal fun setTypedEntityStorage(storage: AbstractEntityStorage) {
    entityStorage = storage
  }

  override fun getAllEntities(): List<WorkspaceEntity> = index.keys.map { entityStorage.entityDataByIdOrDie(it).createEntity(entityStorage) }

  override fun forEach(action: (key: WorkspaceEntity, value: T) -> Unit) {
    index.forEach { (key, value) -> action(entityStorage.entityDataByIdOrDie(key).createEntity(entityStorage), value) }
  }
}

internal class MutableExternalEntityMappingImpl<T> private constructor(
  // Do not write to [index] directly! Create a method in this index and call [startWrite] before write.
  override var index: BidirectionalMap<EntityId, T>,
  private var indexLog: MutableList<IndexLogRecord>,
  private var freezed: Boolean
) : ExternalEntityMappingImpl<T>(index), MutableExternalEntityMapping<T> {

  constructor() : this(BidirectionalMap<EntityId, T>(), mutableListOf(), false)

  override fun addMapping(entity: WorkspaceEntity, data: T) {
    startWrite()
    add((entity as WorkspaceEntityBase).id, data)
    (entityStorage as WorkspaceEntityStorageBuilderImpl).incModificationCount()
  }

  private fun add(id: EntityId, data: T) {
    startWrite()
    index[id] = data
    indexLog.add(IndexLogRecord.Add(id, data))
  }

  override fun addIfAbsent(entity: WorkspaceEntity, data: T): Boolean {
    startWrite()
    entity as WorkspaceEntityBase
    return if (entity.id !in index) {
      add(entity.id, data)
      true
    } else false
  }

  override fun getOrPutDataByEntity(entity: WorkspaceEntity, defaultValue: () -> T): T {
    return getDataByEntity(entity) ?: run {
      startWrite()
      val defaultVal = defaultValue()
      add((entity as WorkspaceEntityBase).id, defaultVal)
      defaultVal
    }
  }

  override fun removeMapping(entity: WorkspaceEntity): T? {
    startWrite()
    entity as WorkspaceEntityBase
    val removed = remove(entity.id)
    (entityStorage as WorkspaceEntityStorageBuilderImpl).incModificationCount()
    return removed
  }

  internal fun clearMapping() {
    startWrite()
    index.clear()
    indexLog.add(IndexLogRecord.Clear)
  }

  internal fun remove(id: EntityId): T? {
    startWrite()
    val removed = index.remove(id)
    indexLog.add(IndexLogRecord.Remove(id))
    return removed
  }

  fun applyChanges(other: MutableExternalEntityMappingImpl<*>, replaceMap: HashBiMap<EntityId, EntityId>) {
    other.indexLog.forEach {
      when (it) {
        is IndexLogRecord.Add<*> -> add(replaceMap.getOrDefault(it.id, it.id), it.data as T)
        is IndexLogRecord.Remove -> remove(replaceMap.getOrDefault(it.id, it.id))
        IndexLogRecord.Clear -> clearMapping()
      }
    }
  }

  private fun startWrite() {
    if (!freezed) return
    this.index = this.index.copy()
    this.indexLog = this.indexLog.toMutableList()
    this.freezed = false
  }

  private fun toImmutable(): ExternalEntityMappingImpl<T> {
    this.freezed = true
    return ExternalEntityMappingImpl(this.index)
  }

  private sealed class IndexLogRecord {
    data class Add<T>(val id: EntityId, val data: T) : IndexLogRecord()
    data class Remove(val id: EntityId) : IndexLogRecord()
    object Clear : IndexLogRecord()
  }

  companion object {
    fun from(other: MutableExternalEntityMappingImpl<*>): MutableExternalEntityMappingImpl<*> =
      MutableExternalEntityMappingImpl(other.index, other.indexLog, true)

    fun fromMap(other: Map<String, ExternalEntityMappingImpl<*>>): MutableMap<String, MutableExternalEntityMappingImpl<*>> {
      val result = mutableMapOf<String, MutableExternalEntityMappingImpl<*>>()
      other.forEach { (identifier, index) ->
        result[identifier] = MutableExternalEntityMappingImpl(index.index, mutableListOf(), true)
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
  override fun getAllEntities(): List<WorkspaceEntity> = emptyList()
  override fun forEach(action: (key: WorkspaceEntity, value: Any) -> Unit) {}
}
