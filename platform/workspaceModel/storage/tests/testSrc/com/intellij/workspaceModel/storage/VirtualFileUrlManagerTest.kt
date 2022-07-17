// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.workspaceModel.storage.impl.url.VirtualFileUrlManagerImpl
import org.junit.Assert.assertEquals
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
    virtualFileManager.add("/a/b/a.txt")
    virtualFileManager.add("/a/b.txt")
    virtualFileManager.add("/c")
    virtualFileManager.add("/a/b/d.txt")
    assertEquals("""
      # 
      # |-  a
      # |    |-  b
      # |    |    |-  a.txt
      # |    |    '-  d.txt
      # |    '-  b.txt
      # '-  c
      #""".trimMargin("#"), virtualFileManager.print())
  }

  @Test
  fun `check insert with duplicates`() {
    virtualFileManager.add("/a/b/a.txt")
    virtualFileManager.add("/a/b/a.txt")
    virtualFileManager.add("/a/b/a.txt")
    virtualFileManager.add("/a/b/a.txt")
    assertEquals("""
      # 
      # '-  a
      #      '-  b
      #           '-  a.txt
      #""".trimMargin("#"), virtualFileManager.print())
  }

  @Test
  fun `check insert and remove same path`() {
    virtualFileManager.add("/a/b/a.txt")
    virtualFileManager.remove("/a/b/a.txt")
    assertEquals("", virtualFileManager.print())
  }

  @Test
  fun `check insert and remove other node`() {
    virtualFileManager.add("/a/b/a.txt")
    virtualFileManager.add("/a/c/a.txt")
    virtualFileManager.remove("/a/b/a.txt")
    assertEquals("""
      # 
      # '-  a
      #      '-  c
      #           '-  a.txt
      #""".trimMargin("#"), virtualFileManager.print())
  }

  @Test
  fun `check remove file with another id`() {
    virtualFileManager.add("/")
    virtualFileManager.add("/a")
    virtualFileManager.remove("/a")
    assertEquals("", virtualFileManager.print())
  }

  @Test
  fun `check filename update`() {
    virtualFileManager.add("/a/b/a.txt")
    assertEquals("""
      # 
      # '-  a
      #      '-  b
      #           '-  a.txt
      #""".trimMargin("#"), virtualFileManager.print())
    virtualFileManager.update("/a/b/a.txt", "/a/b/d.txt")
    assertEquals("""
      # 
      # '-  a
      #      '-  b
      #           '-  d.txt
      #""".trimMargin("#"), virtualFileManager.print())
  }

  @Test
  fun `check update to the existing path`() {
    virtualFileManager.add("/a/b/c.txt")
    virtualFileManager.add("/a/c/d.txt")
    assertEquals("""
      # 
      # '-  a
      #      |-  b
      #      |    '-  c.txt
      #      '-  c
      #           '-  d.txt
      #""".trimMargin("#"), virtualFileManager.print())
    virtualFileManager.update("/a/b/c.txt", "/a/c/d.txt")
    assertEquals("""
      # 
      # '-  a
      #      '-  c
      #           '-  d.txt
      #""".trimMargin("#"), virtualFileManager.print())
  }

  @Test
  fun `check update file and sub folder`() {
    virtualFileManager.add("/a/b/c.txt")
    assertEquals("""
      # 
      # '-  a
      #      '-  b
      #           '-  c.txt
      #""".trimMargin("#"), virtualFileManager.print())
    virtualFileManager.update("/a/b/c.txt", "/a/b/k.txt")
    assertEquals("""
      # 
      # '-  a
      #      '-  b
      #           '-  k.txt
      #""".trimMargin("#"), virtualFileManager.print())
  }

  @Test
  fun `check update with file in root`() {
    virtualFileManager.add("/a/b/c.txt")
    assertEquals("""
      # 
      # '-  a
      #      '-  b
      #           '-  c.txt
      #""".trimMargin("#"), virtualFileManager.print())
    virtualFileManager.update("/a/b/c.txt", "/k.txt")
    assertEquals("""
      # 
      # '-  k.txt
      #""".trimMargin("#"), virtualFileManager.print())
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
    assertEquals("file://", virtualFileManager.fromPath("").url)

    fun assertUrlFromPath(path: String) {
      assertEquals(VfsUtil.pathToUrl(path), virtualFileManager.fromPath(path).url)
    }

    assertUrlFromPath("/main/a.jar")
    assertUrlFromPath("C:\\main\\a.jar")
    assertUrlFromPath("/main/a.jar!/")
    assertUrlFromPath("/main/a.jar!/a.class")
  }

  @Test
  fun `check normalize slashes`() {
    assertEquals("jar://C:/Users/X/a.txt", virtualFileManager.fromUrl("jar://C:/Users\\X\\a.txt").url)
  }

  private fun assertFilePath(expectedResult: String?, url: String) {
    assertEquals(expectedResult, virtualFileManager.fromUrl(url).presentableUrl)
  }

  private fun roundTrip(url: String) {
    assertEquals(url, virtualFileManager.fromUrl(url).url)
  }
}