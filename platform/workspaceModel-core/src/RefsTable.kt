// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api

import kotlin.reflect.KProperty1

open class RefsTable {
  internal open val container: Map<Pair<KProperty1<*, *>, KProperty1<*, *>>, IntIntBiMultiMap> = mapOf()

  operator fun get(local: KProperty1<*, *>, remote: KProperty1<*, *>, localIndex: Int): IntIntMultiMap.IntSequence? {
    val searchByKeys = container[local to remote]
    if (searchByKeys != null) {
      return searchByKeys.getValues(localIndex)
    }

    val searchByValues = container[remote to local]
    if (searchByValues != null) {
      return searchByValues.getKeys(localIndex)
    }

    return null
  }

  fun joinWith(other: RefsTable): RefsTable {
    val newTable = MutableRefsTable()
    newTable.container.putAll(this.container)
    newTable.container.putAll(other.container)
    return newTable
  }
}

class MutableRefsTable : RefsTable() {
  override val container: MutableMap<Pair<KProperty1<*, *>, KProperty1<*, *>>, IntIntBiMultiMap> = mutableMapOf()

  fun remove(left: KProperty1<*, *>,
             right: KProperty1<*, *>,
             id: Int) {
    if (left to right in container) {
      container[left to right]!!.removeKey(id)
    }
    else if (right to left in container) {
      container[right to left]!!.removeValue(id)
    }
  }

  fun <E : PTypedEntity<E>> updateRef(left: KProperty1<*, *>,
                                   right: KProperty1<*, *>,
                                   id: Int,
                                   updateTo: Sequence<E>) {
    when {
      left to right in container -> {
        val table = container[left to right]!!
        table.removeKey(id)
        updateTo.forEach { table.put(id, it.id.arrayId) }
      }
      right to left in container -> {
        val table = container[right to left]!!
        table.removeValue(id)
        updateTo.forEach { table.put(it.id.arrayId, id) }
      }
      else -> {
        val table = IntIntBiMultiMap()
        updateTo.forEach { table.put(id, it.id.arrayId) }
        container[left to right] = table
      }
    }
  }

  fun unorderedCloneTableFrom(local: KProperty1<*, *>, remote: KProperty1<*, *>, other: RefsTable) {
    if (local to remote in other.container) {
      val table = other.container[local to remote]
      this.container[local to remote] = table!!.copy()
    }
    else if (remote to local in other.container) {
      val table = other.container[remote to local]
      this.container[remote to local] = table!!.copy()
    }
  }

  fun clear() = container.clear()
}