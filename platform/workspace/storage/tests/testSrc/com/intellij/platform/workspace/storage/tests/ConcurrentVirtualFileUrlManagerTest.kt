// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.tests

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.platform.workspace.storage.impl.url.ConcurrentVirtualFileUrlManager
import com.intellij.platform.workspace.storage.impl.url.NewVirtualFileUrlImpl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame


class ConcurrentVirtualFileUrlManagerTest {
  private lateinit var virtualFileManager: ConcurrentVirtualFileUrlManager

  @BeforeEach
  fun setUp() {
    virtualFileManager = ConcurrentVirtualFileUrlManager()
  }

  @Test
  fun `check base insert case`() {
    virtualFileManager.getOrCreateFromUrl("/a/b/a.txt")
    virtualFileManager.getOrCreateFromUrl("/a/b.txt")
    virtualFileManager.getOrCreateFromUrl("/c")
    virtualFileManager.getOrCreateFromUrl("/a/b/d.txt")

    // Every inserted path is retrievable and interned to the same (equal) node; a missing path is not.
    for (path in listOf("/a/b/a.txt", "/a/b.txt", "/c", "/a/b/d.txt")) {
      assertEquals(virtualFileManager.getOrCreateFromUrl(path), virtualFileManager.findByUrl(path))
    }
    assertNull(virtualFileManager.findByUrl("/a/b/missing.txt"))

    // Prefix sharing: the subtree under "/a"
    assertEquals(
      setOf(
        virtualFileManager.getOrCreateFromUrl("/a/b"),
        virtualFileManager.getOrCreateFromUrl("/a/b/a.txt"),
        virtualFileManager.getOrCreateFromUrl("/a/b/d.txt"),
        virtualFileManager.getOrCreateFromUrl("/a/b.txt"),
      ),
      virtualFileManager.getOrCreateFromUrl("/a").getSubTreeFileUrls().toSet(),
    )
  }

  @Test
  fun `repeated segment names share a single canonical string instance`() {
    val a = virtualFileManager.getOrCreateFromUrl("/x/src/A.kt")
    val b = virtualFileManager.getOrCreateFromUrl("/y/src/B.kt")

    // The "src" segment lives under two different parents but must be stored as one interned String
    assertSame(
      a.parent!!.fileName,
      b.parent!!.fileName,
      "the 'src' segment under different parents must be the same canonical String instance",
    )
    assertEquals("src", a.parent!!.fileName)
  }

  @Test
  fun `check insert with duplicates`() {
    val first = virtualFileManager.getOrCreateFromUrl("/a/b/a.txt")
    repeat(3) {
      assertEquals(first, virtualFileManager.getOrCreateFromUrl("/a/b/a.txt"))
    }
    // No duplicate nodes are created
    assertEquals(
      setOf(
        virtualFileManager.getOrCreateFromUrl("/a/b"),
        virtualFileManager.getOrCreateFromUrl("/a/b/a.txt"),
      ),
      virtualFileManager.getOrCreateFromUrl("/a").getSubTreeFileUrls().toSet(),
    )
  }

  @Test
  fun `check roundTrip`() {
    roundTrip("")
    roundTrip("/")
    roundTrip("foobar")
    roundTrip("file:///a")
    roundTrip("file:///")
    roundTrip("file://")
    roundTrip("file:////")
    roundTrip("file:///a/")
    roundTrip("jar://C:/Users/X/.m2/repository/org/jetbrains/intellij/deps/jdom/2.0.6/jdom-2.0.6.jar")
    roundTrip("jar://C:/Users/X/.m2/repository/org/jetbrains/intellij/deps/jdom/2.0.6/jdom-2.0.6.jar!/")
    roundTrip("jar://C:/Users/X/.m2/repository/org/jetbrains/intellij/deps/jdom/2.0.6/jdom-2.0.6.jar!//")
    roundTrip("file://C:/Users/user/Monorepo/intellij/community/java/jdkAnnotations")
    roundTrip("//wsl.localhost/Ubuntu/home/test/.jdks/openjdk-20.0.1")
  }

  @Test
  fun `check file path`() {
    assertFilePath("/main/a.jar", "jar:///main/a.jar!/")
    assertFilePath("/main/a.jar", "jar:///main/a.jar!")
    assertFilePath("/main/a.jar", "jar:///main/a.jar")
    assertFilePath("/main/a.jar", "file:///main/a.jar")
    assertFilePath("/main/a.jar!/my/class.class", "jar:///main/a.jar!/my/class.class")
    assertFilePath("", "")
  }

  @Test
  fun `check from path`() {
    assertEquals("file://", virtualFileManager.getOrCreateFromUrl(VfsUtilCore.pathToUrl("")).url)

    fun assertUrlFromPath(path: String) {
      assertEquals(VfsUtil.pathToUrl(path), virtualFileManager.getOrCreateFromUrl(VfsUtilCore.pathToUrl(path)).url)
    }

    assertUrlFromPath("/main/a.jar")
    assertUrlFromPath("C:\\main\\a.jar")
    assertUrlFromPath("/main/a.jar!/")
    assertUrlFromPath("/main/a.jar!/a.class")
  }

  @Test
  fun `check normalize slashes`() {
    assertEquals("jar://C:/Users/X/a.txt", virtualFileManager.getOrCreateFromUrl("jar://C:/Users\\X\\a.txt").url)
  }

  @Test
  fun `the overridable factory makes the empty url`() {
    val manager = FactoryTrackingVirtualFileUrlManager()

    val empty = manager.getOrCreateFromUrl("")

    assertEquals("", empty.url)
    // getOrCreateFromUrl gives the empty URL to callers. Therefore, the empty URL must have the same class as all other
    // nodes. ConcurrentIdeVirtualFileUrlManagerImpl needs this class to be a VirtualFilePointer.
    assertIs<FactoryTrackingVirtualFileUrl>(empty, "createVirtualFileUrl must make the empty URL")
    assertIs<FactoryTrackingVirtualFileUrl>(manager.getOrCreateFromUrl("/a/b"))
    assertSame(empty, manager.getOrCreateFromUrl(""), "the empty URL must be one shared instance")
  }

  @Test
  fun `different nodes can have the same url and must stay different`() {
    // The two empty segments of "/" both have the URL "/". Therefore, equality that uses only getUrl() is not correct.
    val slash = virtualFileManager.getOrCreateFromUrl("/")
    val parent = slash.parent!!

    assertEquals("/", slash.url)
    assertEquals("/", parent.url)
    assertNotSame(slash, parent)
    assertEquals(2, hashSetOf(slash, parent).size, "nodes with the same URL must be two different entries")
  }

  private class FactoryTrackingVirtualFileUrlManager : ConcurrentVirtualFileUrlManager() {
    override fun createVirtualFileUrl(name: String, manager: ConcurrentVirtualFileUrlManager, parent: VirtualFileUrl?): VirtualFileUrl {
      return FactoryTrackingVirtualFileUrl(name, manager, parent as NewVirtualFileUrlImpl?)
    }
  }

  private class FactoryTrackingVirtualFileUrl(
    name: String,
    manager: ConcurrentVirtualFileUrlManager,
    parent: NewVirtualFileUrlImpl?,
  ) : NewVirtualFileUrlImpl(name, manager, parent)

  private fun assertFilePath(expectedResult: String?, url: String) {
    assertEquals(expectedResult, virtualFileManager.getOrCreateFromUrl(url).presentableUrl)
  }

  private fun roundTrip(url: String) {
    assertEquals(url, virtualFileManager.getOrCreateFromUrl(url).url)
  }
}
