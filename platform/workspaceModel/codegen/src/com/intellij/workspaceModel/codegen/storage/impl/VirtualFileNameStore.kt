// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.HashingStrategy
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import org.jetbrains.annotations.TestOnly

internal class VirtualFileNameStore {
  private val generator = IntIdGenerator()
  private val id2NameStore = Int2ObjectOpenHashMap<String>()
  private val name2IdStore = run {
    val checkSensitivityEnabled = Registry.`is`("ide.new.project.model.index.case.sensitivity", false)
    if (checkSensitivityEnabled && !SystemInfoRt.isFileSystemCaseSensitive) return@run CollectionFactory.createCustomHashingStrategyMap<String, IdPerCount>(HashingStrategy.caseInsensitive())
    return@run CollectionFactory.createSmallMemoryFootprintMap<String, IdPerCount>()
  }

  fun generateIdForName(name: String): Int {
    val idPerCount = name2IdStore[name]
    if (idPerCount != null) {
      idPerCount.usageCount++
      return idPerCount.id
    }
    else {
      val id = generator.generateId()

      name2IdStore[name] = IdPerCount(id, 1)
      // Don't convert to links[key] = ... because it *may* became autoboxing
      @Suppress("ReplacePutWithAssignment")
      id2NameStore.put(id, name)
      return id
    }
  }

  fun removeName(name: String) {
    val idPerCount = name2IdStore[name] ?: return
    if (idPerCount.usageCount == 1L) {
      name2IdStore.remove(name)
      id2NameStore.remove(idPerCount.id)
    }
    else {
      idPerCount.usageCount--
    }
  }

  fun getNameForId(id: Int): String? = id2NameStore.get(id)

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