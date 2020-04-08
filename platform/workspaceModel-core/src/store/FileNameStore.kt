// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.store

import com.intellij.util.containers.BidirectionalMap
import java.util.*

class FileNameStore {
  private val generator = IdGenerator()
  private val nameStore = BidirectionalMap<String, IdPerCount>()

  fun getIdForName(name: String): Long {
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
}

private data class IdPerCount(val id: Long, var usageCount: Long)

private class IdGenerator {
  private val freeIdsQueue: Queue<Long> = LinkedList()
  private var generator: Long = 0
  fun generateId() = freeIdsQueue.poll() ?: ++generator
  fun releaseId(id: Long) = freeIdsQueue.add(id)
}