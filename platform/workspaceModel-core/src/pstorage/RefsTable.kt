// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage

import kotlin.reflect.KProperty1

open class RefsTable(
  protected open val container: Map<Pair<KProperty1<*, *>, KProperty1<*, *>>, IntIntBiMultiMap>
) {

  constructor() : this(mapOf())

  operator fun get(local: KProperty1<*, *>, remote: KProperty1<*, *>, localIndex: Int): IntIntMultiMap.IntSequence? {
    val searchByKeys = this[local, remote]
    if (searchByKeys != null) {
      return searchByKeys.getValues(localIndex)
    }

    val searchByValues = this[remote, local]
    if (searchByValues != null) {
      return searchByValues.getKeys(localIndex)
    }

    return null
  }

  operator fun contains(other: Pair<KProperty1<*, *>, KProperty1<*, *>>) = other in container

  operator fun get(left: KProperty1<*, *>, right: KProperty1<*, *>) = container[left to right]

  fun joinWith(other: RefsTable): RefsTable = RefsTable(this.container + other.container)
}

class MutableRefsTable : RefsTable() {
  override val container: MutableMap<Pair<KProperty1<*, *>, KProperty1<*, *>>, IntIntBiMultiMap> = mutableMapOf()

  fun remove(left: KProperty1<*, *>,
             right: KProperty1<*, *>,
             id: Int) {
    if (left to right in container) {
      this[left, right]!!.removeKey(id)
    }
    else if (right to left in container) {
      this[right, left]!!.removeValue(id)
    }
  }

  fun <E : PTypedEntity<E>> updateRef(left: KProperty1<*, *>,
                                      right: KProperty1<*, *>,
                                      id: Int,
                                      updateTo: Sequence<E>) {
    when {
      left to right in container -> {
        val table = this[left, right]!!
        table.removeKey(id)
        updateTo.forEach { table.put(id, it.id.arrayId) }
      }
      right to left in container -> {
        val table = this[right, left]!!
        table.removeValue(id)
        updateTo.forEach { table.put(it.id.arrayId, id) }
      }
      else -> {
        val table = IntIntBiMultiMap()
        updateTo.forEach { table.put(id, it.id.arrayId) }
        this[left, right] = table
      }
    }
  }

  fun unorderedCloneTableFrom(local: KProperty1<*, *>, remote: KProperty1<*, *>, other: RefsTable) {
    if (local to remote in other) {
      val table = other[local, remote]
      this[local, remote] = table!!.copy()
    }
    else if (remote to local in other) {
      val table = other[remote, local]
      this[remote, local] = table!!.copy()
    }
  }

  private operator fun set(left: KProperty1<*, *>, right: KProperty1<*, *>, value: IntIntBiMultiMap) {
    container[left to right] = value
  }

  fun clear() = container.clear()
}