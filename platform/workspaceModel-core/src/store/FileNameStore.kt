// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.store

import com.intellij.util.containers.BidirectionalMap
import org.jetbrains.annotations.TestOnly
import java.util.*

class FileNameStore {
  private val generator = IdGenerator()
  private val nameStore = BidirectionalMap<String, IdPerCount>()

  fun generateIdForName(name: String): Int {
    val idPerCount = nameStore[name]
    if (idPerCount != null) {
      idPerCount.usageCount++
      return idPerCount.id
    } else {
      val id = generator.generateId()
      nameStore[name] = IdPerCount(id, 1)
      return id
    }
  }

  fun removeName(name: String) {
    val idPerCount = nameStore[name] ?: return
    if (idPerCount.usageCount == 1L) {
      nameStore.remove(name)
      generator.releaseId(idPerCount.id)
    } else {
      idPerCount.usageCount--
    }
  }

  fun getNameForId(id: Int): String? {
    val list = nameStore.getKeysByValue(IdPerCount(id, 1)) ?: return null
    if (list.isEmpty()) return null
    assert(list.size == 1)
    return list[0]
  }

  fun getIdForName(name: String) = nameStore[name]?.id

  @TestOnly
  fun clear() {
    nameStore.clear()
    generator.clear()
  }
}

private data class IdPerCount(val id: Int, var usageCount: Long) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as IdPerCount

    if (id != other.id) return false
    return true
  }

  override fun hashCode() = 31 * id.hashCode()
}