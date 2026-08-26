// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class IdValueStore<T : Any> {

  private val valueToId = ConcurrentHashMap<T, String>()

  fun getOrCreateId(value: T): String {
    return valueToId.computeIfAbsent(value) { UUID.randomUUID().toString() }
  }

  fun remove(value: T): String? = valueToId.remove(value)

  fun removeById(id: String): Boolean {
    val entries = valueToId.entries
    val entry = entries.firstOrNull { it.value == id } ?: return false
    return entries.remove(entry)
  }
}
