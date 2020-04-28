// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.external

import com.intellij.util.containers.BidirectionalMap
import com.intellij.workspace.api.TypedEntity
import com.intellij.workspace.api.pstorage.PId
import com.intellij.workspace.api.pstorage.external.ExternalEntityIndex.MutableExternalEntityIndex.IndexLogRecord.Add
import com.intellij.workspace.api.pstorage.external.ExternalEntityIndex.MutableExternalEntityIndex.IndexLogRecord.Remove
import java.util.*

open class ExternalEntityIndex<T> private constructor(internal val index: BidirectionalMap<PId<out TypedEntity>, T>) {
  internal fun getIds(data: T): List<PId<out TypedEntity>>? = index.getKeysByValue(data)
  internal fun getDataById(id: PId<out TypedEntity>): T? = index[id]

  internal fun copyIndex(): BidirectionalMap<PId<out TypedEntity>, T> {
    val copy = BidirectionalMap<PId<out TypedEntity>, T>()
    index.keys.forEach { key -> index[key]?.also { value -> copy[key] = value } }
    return copy
  }

  class MutableExternalEntityIndex<T> private constructor(
    index: BidirectionalMap<PId<out TypedEntity>, T>,
    private val indexLog: MutableList<IndexLogRecord>
  ) : ExternalEntityIndex<T>(index) {
    constructor() : this(BidirectionalMap<PId<out TypedEntity>, T>(), mutableListOf())

    internal fun index(id: PId<out TypedEntity>, data: T) {
      index[id] = data
      indexLog.add(Add(id, data))
    }

    internal fun update(id: PId<out TypedEntity>, newData: T) {
      remove(id)
      index(id, newData)
    }

    internal fun remove(id: PId<out TypedEntity>) {
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
      data class Add<T>(val id: PId<out TypedEntity>, val data: T) : IndexLogRecord()
      data class Remove(val id: PId<out TypedEntity>) : IndexLogRecord()
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