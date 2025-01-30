// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad

import fleet.util.UID
import java.util.concurrent.ConcurrentHashMap

internal class AdRangeMapper<T> {
  private val idToRange = ConcurrentHashMap<UID, T>()
  private val rangeToId = ConcurrentHashMap<T, UID>()

  fun resolveRange(id: UID): T? {
    return idToRange[id]
  }

  fun register(id: UID, range: T) {
    idToRange.put(id, range)
    rangeToId.put(range, id)
  }

  fun unregister(range: T): UID? {
    val id = rangeToId.remove(range)
    if (id != null) {
      idToRange.remove(id)
    }
    return id
  }
}
