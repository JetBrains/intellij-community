// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.jps

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.workspace.ide.VirtualFileUrlManagerImpl
import org.junit.Assert
import org.junit.Before
import org.junit.Test

class VirtualFileUrlManagerTest {
  private lateinit var virtualFileManager: VirtualFileUrlManagerImpl
  @Before
  fun setUp() {
    virtualFileManager = VirtualFileUrlManagerImpl()
  }

  @Test
  fun `check base insert case`() {
    virtualFileManager.add("/a/b/a.txt", 1)
    virtualFileManager.add("/a/b.txt", 2)
    virtualFileManager.add("/c", 3)
    virtualFileManager.add("/a/b/d.txt", 4)
    Assert.assertEquals("""
      # 
      # |-  a
      # |    |-  b
      # |    |    |-  a.txt => [1]
      # |    |    '-  d.txt => [4]
      # |    '-  b.txt => [2]
      # '-  c => [3]
      #""".trimMargin("#"), virtualFileManager.toString())
  }

  @Test
  fun `check insert with duplicates`() {
    virtualFileManager.add("/a/b/a.txt", 1)
    virtualFileManager.add("/a/b/a.txt", 2)
    virtualFileManager.add("/a/b/a.txt", 3)
    virtualFileManager.add("/a/b/a.txt", 3)
    Assert.assertEquals("""
      # 
      # '-  a
      #      '-  b
      #           '-  a.txt => [1, 2, 3]
      #""".trimMargin("#"), virtualFileManager.toString())
  }

  @Test
  fun `check insert and remove same path`() {
    virtualFileManager.add("/a/b/a.txt", 1)
    virtualFileManager.remove("/a/b/a.txt", 1)
    Assert.assertEquals("", virtualFileManager.toString())
  }

  @Test
  fun `check insert and remove other node`() {
    virtualFileManager.add("/a/b/a.txt", 1)
    virtualFileManager.add("/a/c/a.txt", 2)
    virtualFileManager.remove("/a/b/a.txt", 1)
    Assert.assertEquals("""
      # 
      # '-  a
      #      '-  c
      #           '-  a.txt => [2]
      #""".trimMargin("#"), virtualFileManager.toString())
  }

  @Test
  fun `check remove file with another id`() {
    virtualFileManager.add("/", 1)
    virtualFileManager.add("/a", 2)
    virtualFileManager.remove("/a", 1)
    Assert.assertEquals("""
      #  => [1]
      # '-  a => [2]
      #""".trimMargin("#"), virtualFileManager.toString())
  }

  @Test
  fun `check filename update`() {
    virtualFileManager.add("/a/b/a.txt", 1)
    Assert.assertEquals("""
      # 
      # '-  a
      #      '-  b
      #           '-  a.txt => [1]
      #""".trimMargin("#"), virtualFileManager.toString())
    virtualFileManager.update("/a/b/a.txt", "/a/b/d.txt", 1)
    Assert.assertEquals("""
      # 
      # '-  a
      #      '-  b
      #           '-  d.txt => [1]
      #""".trimMargin("#"), virtualFileManager.toString())
  }

  @Test
  fun `check update to the existing path`() {
    virtualFileManager.add("/a/b/c.txt", 1)
    virtualFileManager.add("/a/c/d.txt", 2)
    Assert.assertEquals("""
      # 
      # '-  a
      #      |-  b
      #      |    '-  c.txt => [1]
      #      '-  c
      #           '-  d.txt => [2]
      #""".trimMargin("#"), virtualFileManager.toString())
    virtualFileManager.update("/a/b/c.txt", "/a/c/d.txt", 1)
    Assert.assertEquals("""
      # 
      # '-  a
      #      '-  c
      #           '-  d.txt => [2, 1]
      #""".trimMargin("#"), virtualFileManager.toString())
  }

  @Test
  fun `check update file and sub folder`() {
    virtualFileManager.add("/a/b/c.txt", 1)
    Assert.assertEquals("""
      # 
      # '-  a
      #      '-  b
      #           '-  c.txt => [1]
      #""".trimMargin("#"), virtualFileManager.toString())
    virtualFileManager.update("/a/b/c.txt", "/a/b/k.txt", 1)
    Assert.assertEquals("""
      # 
      # '-  a
      #      '-  b
      #           '-  k.txt => [1]
      #""".trimMargin("#"), virtualFileManager.toString())
  }

  @Test
  fun `check update with file in root`() {
    virtualFileManager.add("/a/b/c.txt", 1)
    Assert.assertEquals("""
      # 
      # '-  a
      #      '-  b
      #           '-  c.txt => [1]
      #""".trimMargin("#"), virtualFileManager.toString())
    virtualFileManager.update("/a/b/c.txt", "/k.txt", 1)
    Assert.assertEquals("""
      # 
      # '-  k.txt => [1]
      #""".trimMargin("#"), virtualFileManager.toString())
  }

  @Test
  fun testRoundTrip() {
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
  }

  @Test
  fun testIsEqualOrParentOf() {
    assertIsEqualOrParentOf(true, "temp:///src", "temp:///src/my")
    assertIsEqualOrParentOf(true, "temp:///src", "temp:///src/my/")
    assertIsEqualOrParentOf(false, "temp:///src", "temp:///srC/my")
    assertIsEqualOrParentOf(false, "temp:///src/x", "temp:///src/y")
    assertIsEqualOrParentOf(false, "file:///src/my", "temp:///src/my")
    assertIsEqualOrParentOf(false, "file:///src/my", "temp:///src/my")
    assertIsEqualOrParentOf(false, "", "temp:///src/my")
    assertIsEqualOrParentOf(false, "temp:///src/my", "")
    assertIsEqualOrParentOf(true, "temp://", "temp:///src/my")
  }

  @Test
  fun testFilePath() {
    assertFilePath(null, "jar:///main/a.jar!/my/class.class")
    assertFilePath("/main/a.jar", "jar:///main/a.jar!/")
    assertFilePath("/main/a.jar", "jar:///main/a.jar!")
    assertFilePath("/main/a.jar", "jar:///main/a.jar")
    assertFilePath("/main/a.jar", "file:///main/a.jar")
    assertFilePath(null, "")
  }

  @Test
  fun testFromPath() {
    Assert.assertEquals("", virtualFileManager.fromPath("").url)

    fun assertUrlFromPath(path: String) {
      Assert.assertEquals(VfsUtil.pathToUrl(path), virtualFileManager.fromPath(path).url)
    }

    assertUrlFromPath("/main/a.jar")
    assertUrlFromPath("C:\\main\\a.jar")
    assertUrlFromPath("/main/a.jar!/")
    assertUrlFromPath("/main/a.jar!/a.class")
  }

  @Test
  fun normalizeSlashes() {
    Assert.assertEquals("jar://C:/Users/X/a.txt", virtualFileManager.fromUrl("jar://C:/Users\\X\\a.txt").url)
  }

  private fun assertFilePath(expectedResult: String?, url: String) {
    Assert.assertEquals(expectedResult, virtualFileManager.fromUrl(url).filePath)
  }

  private fun assertIsEqualOrParentOf(expectedResult: Boolean, parentString: String, childString: String) {
    val parent = virtualFileManager.fromUrl(parentString)
    val child = virtualFileManager.fromUrl(childString)
    Assert.assertTrue("'$parent'.isEqualOrParentOf('$parent')", parent.isEqualOrParentOf(parent))
    Assert.assertTrue("'$child'.isEqualOrParentOf('$child')", child.isEqualOrParentOf(child))
    Assert.assertEquals(
      "'$parent'.isEqualOrParentOf('$child') should be ${if (expectedResult) "true" else "false"}",
      expectedResult,
      parent.isEqualOrParentOf(child))
  }

  private fun roundTrip(url: String) {
    Assert.assertEquals(url, virtualFileManager.fromUrl(url).url)
  }

}