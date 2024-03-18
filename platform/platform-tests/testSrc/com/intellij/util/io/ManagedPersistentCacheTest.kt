// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

open class ManagedPersistentCacheTest : ManagedCacheTestBase() {

  override fun createCache(): ManagedPersistentCache<Int, Int> {
    return ManagedPersistentCache("test-cache", mapBuilder(), false)
  }

  @Test
  open fun testNullsWhenCorrupted() {
    withCache { cache ->
      cache[1] = 10
    }
    withCache { cache ->
      corruptCache()
      cache[2] = 20
      assertFalse(cache.isClosed())
      assertNull(cache[1], "value expected to be null when cache is corrupted")
      assertNull(cache[2], "value expected to be null when cache is corrupted")
    }
  }

  @Test
  fun testCloseOnIOExceptions() {
    withCache { cache ->
      cache[1] = 10
    }
    withCache { cache ->
      corruptCache()
      repeat(30) {
        cache[2] = 20
      }
      assertTrue(cache.isClosed(), "cache expected to be closed when error count exceeds threshold")
    }
  }
}
