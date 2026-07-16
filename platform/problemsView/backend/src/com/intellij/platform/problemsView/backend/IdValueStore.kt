// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.problemsView.backend

import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
class IdValueStore<T : Any> {

  private val valueToId = ConcurrentHashMap<T, String>()

  @OptIn(AwaitCancellationAndInvoke::class)
  fun getOrCreateId(value: T, scope: CoroutineScope): String {
    valueToId[value]?.let { return it }

    val newId = UUID.randomUUID().toString()
    valueToId[value] = newId

    scope.awaitCancellationAndInvoke {
      remove(value)
    }

    return newId
  }

  fun findValueById(id: String): T? {
    return valueToId.entries.firstOrNull { it.value == id }?.key
  }

  fun remove(value: T): String? {
    return valueToId.remove(value)
  }

  fun getSize(): Int = valueToId.size

  fun getSample(count: Int): List<Pair<T, String>> {
    return valueToId.entries
      .take(count)
      .map { (value, id) -> value to id }
  }
}
