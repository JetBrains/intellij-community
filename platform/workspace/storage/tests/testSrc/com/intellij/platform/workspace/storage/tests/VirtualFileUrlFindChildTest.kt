// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class VirtualFileUrlFindChildTest {
  private lateinit var virtualFileManager: VirtualFileUrlManagerImpl

  @BeforeEach
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun `returns the existing direct child`() {
    val child = virtualFileManager.storeAndGet("file:///a/b.txt")
    val parent = virtualFileManager.storeAndGet("file:///a")

    assertSame(child, parent.findChild("b.txt"))
  }

  @Test
  fun `returns an intermediate node that is not registered`() {
    val leaf = virtualFileManager.storeAndGet("file:///a/b/c.txt")
    val parent = virtualFileManager.storeAndGet("file:///a")

    val intermediate = parent.findChild("b")
    assertSame(leaf.parent, intermediate)
    assertNull(virtualFileManager.get("file:///a/b"), "the intermediate node must stay not registered")
  }

  @Test
  fun `returns null for a node without children`() {
    val leaf = virtualFileManager.storeAndGet("file:///a/b.txt")

    assertNull(leaf.findChild("c.txt"))
    assertNull(leaf.findChild(""))
  }

  @Test
  fun `returns null for a missing child`() {
    val parent = virtualFileManager.storeAndGet("file:///a")
    virtualFileManager.storeAndGet("file:///a/b.txt")

    assertNull(parent.findChild("c.txt"))
  }

  @Test
  fun `finds only direct children`() {
    val parent = virtualFileManager.storeAndGet("file:///a")
    virtualFileManager.storeAndGet("file:///a/b/c.txt")

    assertNull(parent.findChild("c.txt"))
    assertNull(parent.findChild("b/c.txt"))
  }

  @Test
  fun `matches the name with case sensitivity`() {
    val parent = virtualFileManager.storeAndGet("file:///a")
    virtualFileManager.storeAndGet("file:///a/b.txt")

    assertNull(parent.findChild("B.txt"))
  }

  @Test
  fun `returns the child for an empty name segment`() {
    val parent = virtualFileManager.storeAndGet("file:///a")
    val withTrailingSlash = virtualFileManager.storeAndGet("file:///a/")

    assertSame(withTrailingSlash, parent.findChild(""))
  }

  @Test
  fun `does not create a child`() {
    val parent = virtualFileManager.storeAndGet("file:///a")
    virtualFileManager.storeAndGet("file:///a/b.txt")
    val subTreeBefore = parent.getSubTreeFileUrls().toSet()

    assertNull(parent.findChild("c.txt"))

    assertEquals(subTreeBefore, parent.getSubTreeFileUrls().toSet())
  }

  @Test
  fun `finds a child that append creates`() {
    val parent = virtualFileManager.storeAndGet("file:///a")
    val appended = parent.append("b.txt")

    assertSame(appended, parent.findChild("b.txt"))
  }

  @Test
  fun `returns null for the empty url`() {
    virtualFileManager.storeAndGet("file:///a")

    assertNull(virtualFileManager.storeAndGet("").findChild("file:"))
  }
}
