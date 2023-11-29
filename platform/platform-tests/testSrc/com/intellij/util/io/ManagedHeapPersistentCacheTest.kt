// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ManagedHeapPersistentCacheTest : ManagedCacheTestBase() {

  override fun createCache(): ManagedHeapPersistentCache<Int, Int> {
    return ManagedHeapPersistentCache("test-cache", mapBuilder(), inMemoryCount=5, closeAppOnShutdown=false)
  }

  @Test
  fun testSpillToDisk() {
    withCache { cache ->
      repeat(100) { i ->
        cache[i] = i
      }
      repeat(100) { i->
        assertEquals(i, cache[i], "value expected to be spilled to disk and to be read after")
      }
    }
  }
}
