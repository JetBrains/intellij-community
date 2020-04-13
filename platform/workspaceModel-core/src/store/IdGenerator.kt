// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.store

import org.jetbrains.annotations.TestOnly
import java.util.*

internal class IdGenerator {
  private val freeIdsQueue: Queue<Int> = LinkedList()
  private var generator: Int = 0
  fun generateId() = freeIdsQueue.poll() ?: ++generator
  fun releaseId(id: Int) = freeIdsQueue.add(id)

  @TestOnly
  fun clear() {
    generator = 0
    freeIdsQueue.clear()
  }
}