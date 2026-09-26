// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import org.jetbrains.annotations.ApiStatus
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
class IdValueStore<T : Any> {

  private val valueToId = ConcurrentHashMap<T, String>()

  fun getOrCreateId(value: T): String {
    return valueToId.computeIfAbsent(value) { UUID.randomUUID().toString() }
  }

  fun findValueById(id: String): T? {
    return valueToId.entries.firstOrNull { it.value == id }?.key
  }

  fun remove(value: T): String? = valueToId.remove(value)

  fun removeById(id: String): Boolean {
    val entries = valueToId.entries
    val entry = entries.firstOrNull { it.value == id } ?: return false
    return entries.remove(entry)
  }

  fun getSize(): Int = valueToId.size
}
