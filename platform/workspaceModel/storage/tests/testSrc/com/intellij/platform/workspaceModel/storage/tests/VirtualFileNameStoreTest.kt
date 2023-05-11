// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspaceModel.storage.tests

import com.intellij.workspaceModel.storage.impl.VirtualFileNameStore
import org.junit.Assert
import org.junit.Test

class VirtualFileNameStoreTest {
  @Test
  fun `test 1`() {
    val store = VirtualFileNameStore()
    Assert.assertEquals(1, store.generateIdForName("a"))
    Assert.assertEquals(2, store.generateIdForName("b"))
    Assert.assertEquals(3, store.generateIdForName("c"))
  }

  @Test
  fun `test 2`() {
    val store = VirtualFileNameStore()
    Assert.assertEquals(1, store.generateIdForName("a"))
    Assert.assertEquals(1, store.generateIdForName("a"))
  }

  @Test
  fun `test 3`() {
    val store = VirtualFileNameStore()
    Assert.assertEquals(1, store.generateIdForName("a"))
    Assert.assertEquals(2, store.generateIdForName("b"))
    Assert.assertEquals(3, store.generateIdForName("c"))
    store.removeName("b")
    store.removeName("a")
    Assert.assertEquals(4, store.generateIdForName("e"))
    Assert.assertEquals(5, store.generateIdForName("f"))
  }
}