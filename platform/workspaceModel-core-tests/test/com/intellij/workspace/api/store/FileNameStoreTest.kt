// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.store

import org.junit.Assert.assertEquals
import org.junit.Test


class FileNameStoreTest {
  @Test
  fun `test 1`() {
    val store = FileNameStore()
    assertEquals(1, store.generateIdForName("a"))
    assertEquals(2, store.generateIdForName("b"))
    assertEquals(3, store.generateIdForName("c"))
  }

  @Test
  fun `test 2`() {
    val store = FileNameStore()
    assertEquals(1, store.generateIdForName("a"))
    assertEquals(1, store.generateIdForName("a"))
  }

  @Test
  fun `test 3`() {
    val store = FileNameStore()
    assertEquals(1, store.generateIdForName("a"))
    assertEquals(2, store.generateIdForName("b"))
    assertEquals(3, store.generateIdForName("c"))
    store.removeName("b")
    store.removeName("a")
    assertEquals(2, store.generateIdForName("e"))
    assertEquals(1, store.generateIdForName("f"))
  }
}