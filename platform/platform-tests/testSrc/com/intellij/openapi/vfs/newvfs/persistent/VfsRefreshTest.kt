// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.openapi.vfs.newvfs.RefreshQueueImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReferenceArray

@TestApplication
class VfsRefreshTest {

  @Test
  fun `parallelization guard stress test`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val map = ConcurrentHashMap<Any, Pair<Semaphore, Int>>()
    val numberOfKeys = 10
    val arrayOfKeys = AtomicReferenceArray(Array(numberOfKeys) { Any() })
    val arrayOfAtomicRefs = AtomicReferenceArray(Array(numberOfKeys) { AtomicInteger(0) })
    coroutineScope {
      repeat(1000) { id ->
        val identifier = id % numberOfKeys
        launch {
          RefreshQueueImpl.executeWithParallelizationGuard(arrayOfKeys[identifier], map) {
            val counter = arrayOfAtomicRefs[identifier].incrementAndGet()
            assertThat(counter).isEqualTo(1)
            arrayOfAtomicRefs[identifier].decrementAndGet()
          }
        }
      }
    }
    assertThat(map).isEmpty()
  }
}