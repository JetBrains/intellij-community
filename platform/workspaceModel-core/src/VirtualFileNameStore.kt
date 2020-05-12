// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api

import gnu.trove.THashMap
import org.jetbrains.annotations.TestOnly

internal class VirtualFileNameStore {
  private val generator = IntIdGenerator()
  private val name2IdStore = THashMap<String, IdPerCount>()
  private val id2NameStore = THashMap<Int, String>()

  fun generateIdForName(name: String): Int {
    val idPerCount = name2IdStore[name]
    if (idPerCount != null) {
      idPerCount.usageCount++
      return idPerCount.id
    } else {
      val id = generator.generateId()
      name2IdStore[name] = IdPerCount(id, 1)
      id2NameStore[id] = name
      return id
    }
  }

  fun removeName(name: String) {
    val idPerCount = name2IdStore[name] ?: return
    if (idPerCount.usageCount == 1L) {
      name2IdStore.remove(name)
      id2NameStore.remove(idPerCount.id)
    } else {
      idPerCount.usageCount--
    }
  }

  fun getNameForId(id: Int): String? = id2NameStore[id]

  fun getIdForName(name: String) = name2IdStore[name]?.id

  @TestOnly
  fun clear() {
    name2IdStore.clear()
    id2NameStore.clear()
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

internal class IntIdGenerator {
  private var generator: Int = 0
  fun generateId() = ++generator

  @TestOnly
  fun clear() {
    generator = 0
  }
}