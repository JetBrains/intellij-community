// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.external

import com.intellij.util.containers.BidirectionalMap
import com.intellij.workspace.api.TypedEntity
import com.intellij.workspace.api.pstorage.PId
import com.intellij.workspace.api.pstorage.external.ExternalStorageIndex.MutableExternalStorageIndex.IndexLogRecord.Add
import com.intellij.workspace.api.pstorage.external.ExternalStorageIndex.MutableExternalStorageIndex.IndexLogRecord.Remove
import java.util.*

open class ExternalStorageIndex<T> private constructor(internal val index: BidirectionalMap<PId<out TypedEntity>, T>) {
  internal fun getIds(data: T): List<PId<out TypedEntity>>? = index.getKeysByValue(data)
  internal fun getDataById(id: PId<out TypedEntity>): T? = index[id]

  internal fun copyIndex(): BidirectionalMap<PId<out TypedEntity>, T> {
    val copy = BidirectionalMap<PId<out TypedEntity>, T>()
    index.keys.forEach { key -> index[key]?.also { value -> copy[key] = value } }
    return copy
  }

  class MutableExternalStorageIndex<T> private constructor(
    index: BidirectionalMap<PId<out TypedEntity>, T>,
    private val indexLog: MutableList<IndexLogRecord>
  ) : ExternalStorageIndex<T>(index) {
    constructor() : this(BidirectionalMap<PId<out TypedEntity>, T>(), mutableListOf())

    internal fun index(id: PId<out TypedEntity>, record: T) {
      index[id] = record
      indexLog.add(Add(id, record))
    }

    internal fun remove(id: PId<out TypedEntity>) {
      index.remove(id)
      indexLog.add(Remove(id))
    }

    fun applyChanges(other: MutableExternalStorageIndex<*>) {
      other.indexLog.forEach {
        when (it) {
          is Add<*> -> index(it.id, it.data as T)
          is Remove -> remove(it.id)
        }
      }
    }

    private fun toImmutable(): ExternalStorageIndex<T> = ExternalStorageIndex(copyIndex())

    private sealed class IndexLogRecord {
      data class Add<T>(val id: PId<out TypedEntity>, val data: T) : IndexLogRecord()
      data class Remove(val id: PId<out TypedEntity>) : IndexLogRecord()
    }

    companion object {
      fun from(other: Map<String, ExternalStorageIndex<*>>): MutableMap<String, MutableExternalStorageIndex<*>> {
        val result = mutableMapOf<String, MutableExternalStorageIndex<*>>()
        other.forEach { (identifier, index) ->
          result[identifier] = MutableExternalStorageIndex(index.copyIndex(), mutableListOf())
        }
        return result
      }

      fun fromMutable(other: MutableMap<String, MutableExternalStorageIndex<*>>): MutableMap<String, MutableExternalStorageIndex<*>> {
        val result = mutableMapOf<String, MutableExternalStorageIndex<*>>()
        other.forEach { (identifier, index) ->
          result[identifier] = MutableExternalStorageIndex(index.copyIndex(), index.indexLog.toMutableList())
        }
        return result
      }

      fun toImmutable(other: MutableMap<String, MutableExternalStorageIndex<*>>): Map<String, ExternalStorageIndex<*>> {
        val result = mutableMapOf<String, ExternalStorageIndex<*>>()
        other.forEach { (identifier, index) ->
          result[identifier] = index.toImmutable()
        }
        return Collections.unmodifiableMap(result)
      }
    }
  }
}