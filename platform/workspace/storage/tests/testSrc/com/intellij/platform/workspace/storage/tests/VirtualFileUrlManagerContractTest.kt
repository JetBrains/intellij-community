// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.platform.workspace.storage.impl.url.ConcurrentVirtualFileUrlManager
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

abstract class AbstractVirtualFileUrlManagerContractTest {
  protected abstract fun createManager(): VirtualFileUrlManager

  private lateinit var manager: VirtualFileUrlManager

  @BeforeEach
  fun setUp() {
    manager = createManager()
  }

  @Test
  fun `distinct urls sharing the last segment are not equal`() {
    val a = manager.storeAndGet("/a/x")
    val b = manager.storeAndGet("/b/x")

    assertNotEquals(a, b, "VirtualFileUrls for /a/x and /b/x represent different URLs and must not be equal")
    assertEquals(2, hashSetOf(a, b).size, "distinct URLs must not collapse into one HashSet entry")
  }

  @Test
  fun `findByUrl returns null for an intermediate node that was never registered`() {
    manager.storeAndGet("/a/b")

    assertNull(manager.get("/a"), "/a was only an intermediate segment and was never registered")
  }


  @Test
  fun `append strips a single leading slash`() {
    val parent = manager.storeAndGet("file:///a")

    assertEquals("file:///a/foo", parent.append("/foo").url, "append must not introduce an empty segment")
  }


  @Test
  fun `empty url is not registered and is not found`() {
    manager.storeAndGet("")

    assertNull(manager.get(""), "the empty URL must not be interned as a trie node")
  }


  @Test
  fun `parent of a top-level segment is null`() {
    assertNull(manager.storeAndGet("foobar").parent, "a root-level segment has no parent")
  }


  @Test
  fun `parent of an absolute top-level path is the filesystem root`() {
    assertEquals(
      "/",
      manager.storeAndGet("/a").parent?.url,
      "the parent of /a is the filesystem root /",
    )
  }


  @Test
  fun `getOrCreateFromUrl returns the same cached instance for the same url`() {
    assertSame(
      manager.storeAndGet("/a/b"),
      manager.storeAndGet("/a/b"),
      "repeated getOrCreateFromUrl for the same URL must return the cached instance",
    )
  }

}

class VirtualFileUrlManagerImplContractTest : AbstractVirtualFileUrlManagerContractTest() {
  override fun createManager(): VirtualFileUrlManager = VirtualFileUrlManagerImpl()
}

class ConcurrentVirtualFileUrlManagerContractTest : AbstractVirtualFileUrlManagerContractTest() {
  override fun createManager(): VirtualFileUrlManager = ConcurrentVirtualFileUrlManager()
}
